package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.common.util.ConversationIdUtil;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.ops.observability.annotation.LLMTrace;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.core.TraceSanitizer;
import com.github.app.dify.ops.trace.core.TraceStepCollector;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.permission.service.UserKnowledgeBaseVisibilityService;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp;
import com.github.app.dify.knowledgebase.langchain4j.StreamingChatLanguageModel;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseQAResponse;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseQAService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseService;
import com.github.app.dify.memory.service.UserMemoryService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.knowledgebase.service.RagRetrievalService;
import com.github.app.dify.system.config.RagConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.app.dify.system.util.SkillLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库问答服务（使用LangChain4j RAG）
 */
@Service
public class KnowledgeBaseQAServiceImpl implements KnowledgeBaseQAService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseQAServiceImpl.class);

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired
    private ModelConfigService modelConfigService;

    @Autowired
    private ContextCompressionService contextCompressionService;

    @Autowired
    private RagConfig ragConfig;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private UserKnowledgeBaseVisibilityService userKnowledgeBaseVisibilityService;

    @Autowired
    private UserMemoryService userMemoryService;

    @Autowired
    private TraceFacade traceFacade;

    @Autowired
    private TraceSanitizer traceSanitizer;

    /**
     * 问答（非流式）
     */
    @Override
    @LLMTrace(
            traceSource = "Knowledge Base QA",
            conversationIdParam = "request.conversationId",
            extractFromReturn = true
    )
    public KnowledgeBaseQAResponse answer(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId,
            Integer userRole) {
        TraceHandle traceHandle = startBusinessTrace("knowledge_base_qa", knowledgeBaseId, userId, request, false);
        TraceStepCollector stepCollector = new TraceStepCollector(traceFacade, traceHandle, traceSanitizer);
        try {
            stepCollector.trace("KB_ACCESS_CHECK", "知识库访问校验",
                    "kbId=" + knowledgeBaseId + ",userId=" + userId + ",userRole=" + userRole,
                    () -> {
                        validateKnowledgeBaseAccess(knowledgeBaseId, userId, userRole);
                        return true;
                    },
                    ok -> "access_granted=" + ok);

            Map<String, Object> kbConfig = stepCollector.trace(
                    "KB_CONFIG_LOAD",
                    "加载知识库检索配置",
                    "kbId=" + knowledgeBaseId,
                    () -> {
                        Map<String, Object> configMap = new HashMap<>();
                        configMap.put("embeddingModelId", null);
                        configMap.put("topK", null);
                        try {
                            KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
                            configMap.put("embeddingModelId", kb.getEmbeddingModelId());
                            configMap.put("topK", kb.getTopK());
                        } catch (Exception e) {
                            logger.warn("获取知识库配置失败，使用默认配置 - 知识库ID: {}", knowledgeBaseId, e);
                        }
                        return configMap;
                    },
                    config -> config);
            Long embeddingModelId = (Long) kbConfig.get("embeddingModelId");
            Integer topK = (Integer) kbConfig.get("topK");

            List<RagRetrievalService.RetrievalResult> retrievalResults = stepCollector.trace(
                    "RAG_RETRIEVE",
                    "执行知识库检索",
                    "kbId=" + knowledgeBaseId + ",question=" + request.getQuestion(),
                    () -> ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion(), embeddingModelId, topK),
                    results -> "result_count=" + (results == null ? 0 : results.size()));

            if (retrievalResults.isEmpty()) {
                KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                response.setAnswer("抱歉，在知识库中没有找到相关信息。");
                response.setSources(new ArrayList<>());
                traceFacade.success(traceHandle, response.getAnswer());
                return response;
            }

            String memoryContext = stepCollector.trace(
                    "MEMORY_BUILD",
                    "构建用户记忆上下文",
                    "userId=" + userId + ",kbId=" + knowledgeBaseId,
                    () -> userMemoryService.buildMemoryContext(userId, request.getQuestion(), "knowledge_base",
                            knowledgeBaseId),
                    context -> "memory_length=" + (context == null ? 0 : context.length()));

            List<ChatMessage> messages = stepCollector.trace(
                    "MESSAGES_BUILD",
                    "构建LLM消息列表",
                    "history_count=" + (request.getHistory() == null ? 0 : request.getHistory().size()),
                    () -> buildMessages(request, memoryContext),
                    built -> "message_count=" + (built == null ? 0 : built.size()));

            // 记录历史对话信息
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                logger.info("使用历史对话，历史消息数量: {}", request.getHistory().size());
            }
            logger.debug("构建的消息列表大小: {}", messages.size());

            List<ChatMessage> messagesBeforeCompress = messages;
            messages = stepCollector.trace(
                    "CONTEXT_COMPRESS",
                    "执行上下文压缩",
                    "before_size=" + messagesBeforeCompress.size(),
                    () -> contextCompressionService.compressContext(messagesBeforeCompress, request),
                    compressed -> "after_size=" + (compressed == null ? 0 : compressed.size()));
            logger.debug("压缩后的消息列表大小: {}", messages.size());

            List<ChatMessage> finalMessages = messages;
            Response<AiMessage> aiResponse = stepCollector.trace(
                    "LLM_GENERATE",
                    "执行知识增强回答生成",
                    "kbId=" + knowledgeBaseId + ",retrieval_count=" + retrievalResults.size(),
                    () -> generateAnswerWithHistory(finalMessages, knowledgeBaseId, request, memoryContext, retrievalResults),
                    resp -> "generated=" + (resp != null));
            String answer = aiResponse.content().text();

            // 提取token使用信息和模型ID
            Long modelId = request.getModelId();
            QAModel qaModel = null;
            try {
                if (modelId != null) {
                    qaModel = modelConfigService.getQAModelById(modelId);
                } else {
                    qaModel = modelConfigService.getDefaultQAModelForRAG();
                }
                if (qaModel != null) {
                    modelId = qaModel.getId();
                }
            } catch (Exception e) {
                logger.debug("获取模型信息失败: {}", e.getMessage());
            }

            Long promptTokens = null;
            Long completionTokens = null;
            Long totalTokens = null;

            // 尝试从Response中获取token使用信息
            try {
                // langchain4j的Response可能包含tokenUsage信息
                dev.langchain4j.model.output.TokenUsage tokenUsage = aiResponse.tokenUsage();

                if (tokenUsage != null) {
                    promptTokens = tokenUsage.inputTokenCount() != null ? (long) tokenUsage.inputTokenCount() : null;
                    completionTokens = tokenUsage.outputTokenCount() != null ? (long) tokenUsage.outputTokenCount()
                            : null;
                    totalTokens = tokenUsage.totalTokenCount() != null ? (long) tokenUsage.totalTokenCount() : null;
                    logger.info("获取到Token使用信息 - Prompt: {}, Completion: {}, Total: {}",
                            promptTokens, completionTokens, totalTokens);
                } else {
                    logger.debug("Response对象中tokenUsage为null，尝试估算Token使用量");
                    // 如果无法获取token信息，尝试基于内容估算
                    Long[] estimated = estimateTokenUsage(request.getQuestion(), answer);
                    promptTokens = estimated[0];
                    completionTokens = estimated[1];
                    totalTokens = estimated[2];
                }
            } catch (NoSuchMethodError e) {
                logger.debug("Response对象不包含tokenUsage方法，尝试估算Token使用量: {}", e.getMessage());
                // 如果方法不存在，尝试基于内容估算
                Long[] estimated = estimateTokenUsage(request.getQuestion(), answer);
                promptTokens = estimated[0];
                completionTokens = estimated[1];
                totalTokens = estimated[2];
            } catch (Exception e) {
                logger.warn("无法获取Token使用信息: {}", e.getMessage());
                logger.debug("Token获取异常详情", e);
                // 尝试基于内容估算
                Long[] estimated = estimateTokenUsage(request.getQuestion(), answer);
                promptTokens = estimated[0];
                completionTokens = estimated[1];
                totalTokens = estimated[2];
            }

            // 保存历史记录
            Long conversationId = null;
            try {
                Long requestConversationId = ConversationIdUtil.parseConversationId(request.getConversationId(), logger);
                conversationId = chatHistoryService.getOrCreateConversation(
                        userId, requestConversationId, 2, null, knowledgeBaseId, request.getQuestion());
                logger.info("非流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}",
                        requestConversationId, conversationId);
                // 保存用户消息（不需要token信息）
                chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                // 保存助手消息（带token信息）
                chatHistoryService.saveMessage(conversationId, "assistant", answer,
                        modelId, promptTokens, completionTokens, totalTokens);
                try {
                    userMemoryService.updateMemoryAsync(userId, request.getQuestion(), answer, modelId,
                            conversationId, "knowledge_base", knowledgeBaseId);
                } catch (Exception e) {
                    logger.debug("触发异步记忆更新失败（知识库问答）", e);
                }
            } catch (Exception e) {
                logger.error("保存历史记录失败", e);
                // 不抛出异常，避免影响主流程
            }

            // 构建响应
            KnowledgeBaseQAResponse kbResponse = new KnowledgeBaseQAResponse();
            kbResponse.setAnswer(answer);
            kbResponse.setConversationId(conversationId != null ? conversationId.toString() : null);
            kbResponse.setSources(retrievalResults.stream().map(r -> {
                KnowledgeBaseQAResponse.SourceDocument source = new KnowledgeBaseQAResponse.SourceDocument();
                source.setDocumentId(r.getDocumentId());
                source.setChunkIndex(r.getChunkIndex());
                source.setText(r.getText());
                source.setScore(r.getScore());
                return source;
            }).collect(Collectors.toList()));

            logger.info("知识库问答完成 - 知识库ID: {}, 问题: {}", knowledgeBaseId, request.getQuestion());

            traceFacade.success(traceHandle, kbResponse.getAnswer());
            return kbResponse;

        } catch (Exception e) {
            traceFacade.error(traceHandle, e);
            logger.error("知识库问答失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new BusinessException("知识库问答失败", ErrorCode.SYSTEM_BUSY, e);
        }
    }

    /**
     * 问答（流式）
     */
    @Override
    @LLMTrace(
            traceSource = "Knowledge Base QA",
            conversationIdParam = "request.conversationId"
    )
    public Flux<KnowledgeBaseQAResponse> answerStream(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId,
            Integer userRole) {
        TraceHandle traceHandle = startBusinessTrace("knowledge_base_qa_stream", knowledgeBaseId, userId, request, true);
        TraceStepCollector stepCollector = new TraceStepCollector(traceFacade, traceHandle, traceSanitizer);
        try {
            stepCollector.trace("KB_ACCESS_CHECK", "知识库访问校验（流式）",
                    "kbId=" + knowledgeBaseId + ",userId=" + userId + ",userRole=" + userRole,
                    () -> {
                        validateKnowledgeBaseAccess(knowledgeBaseId, userId, userRole);
                        return true;
                    },
                    ok -> "access_granted=" + ok);

            // 获取知识库的配置信息（向量化模型ID和topK）
            Map<String, Object> kbConfig = stepCollector.trace(
                    "KB_CONFIG_LOAD",
                    "加载知识库检索配置（流式）",
                    "kbId=" + knowledgeBaseId,
                    () -> {
                        Map<String, Object> configMap = new HashMap<>();
                        configMap.put("embeddingModelId", null);
                        configMap.put("topK", null);
                        try {
                            com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp kb = knowledgeBaseService
                                    .getKnowledgeBaseById(knowledgeBaseId);
                            configMap.put("embeddingModelId", kb.getEmbeddingModelId());
                            configMap.put("topK", kb.getTopK());
                        } catch (Exception e) {
                            logger.warn("获取知识库配置失败，使用默认配置 - 知识库ID: {}", knowledgeBaseId, e);
                        }
                        return configMap;
                    },
                    config -> config);
            Long embeddingModelId = (Long) kbConfig.get("embeddingModelId");
            Integer topK = (Integer) kbConfig.get("topK");

            // 先检索相关文档（用于构建sources）
            List<RagRetrievalService.RetrievalResult> retrievalResults = stepCollector.trace(
                    "RAG_RETRIEVE",
                    "执行知识库检索（流式）",
                    "kbId=" + knowledgeBaseId + ",question=" + request.getQuestion(),
                    () -> ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion(), embeddingModelId, topK),
                    results -> "result_count=" + (results == null ? 0 : results.size()));

            if (retrievalResults.isEmpty()) {
                KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                response.setAnswer("抱歉，在知识库中没有找到相关信息。");
                response.setFinished(true);
                response.setSources(new ArrayList<>());
                traceFacade.success(traceHandle, response.getAnswer());
                return Flux.just(response);
            }

            // 构建sources
            List<KnowledgeBaseQAResponse.SourceDocument> sources = retrievalResults.stream().map(r -> {
                KnowledgeBaseQAResponse.SourceDocument source = new KnowledgeBaseQAResponse.SourceDocument();
                source.setDocumentId(r.getDocumentId());
                source.setChunkIndex(r.getChunkIndex());
                source.setText(r.getText());
                source.setScore(r.getScore());
                return source;
            }).collect(Collectors.toList());

            String memoryContext = stepCollector.trace(
                    "MEMORY_BUILD",
                    "构建用户记忆上下文（流式）",
                    "userId=" + userId + ",kbId=" + knowledgeBaseId,
                    () -> userMemoryService.buildMemoryContext(userId, request.getQuestion(), "knowledge_base",
                            knowledgeBaseId),
                    context -> "memory_length=" + (context == null ? 0 : context.length()));

            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = stepCollector.trace(
                    "MESSAGES_BUILD",
                    "构建LLM消息列表（流式）",
                    "history_count=" + (request.getHistory() == null ? 0 : request.getHistory().size()),
                    () -> buildMessages(request, memoryContext),
                    built -> "message_count=" + (built == null ? 0 : built.size()));

            // 应用上下文压缩策略
            List<ChatMessage> messagesBeforeCompress = messages;
            messages = stepCollector.trace(
                    "CONTEXT_COMPRESS",
                    "执行上下文压缩（流式）",
                    "before_size=" + messagesBeforeCompress.size(),
                    () -> contextCompressionService.compressContext(messagesBeforeCompress, request),
                    compressed -> "after_size=" + (compressed == null ? 0 : compressed.size()));
            logger.debug("压缩后的消息列表大小（流式）: {}", messages.size());

            Flux<KnowledgeBaseQAResponse> streamFlux = generateStreamAnswerWithHistory(messages, knowledgeBaseId, request,
                    sources, userId, memoryContext, retrievalResults);
            return streamFlux
                    .doOnComplete(() -> traceFacade.success(traceHandle, "stream_finished"))
                    .doOnError(error -> traceFacade.error(traceHandle, error));

        } catch (Exception e) {
            traceFacade.error(traceHandle, e);
            logger.error("知识库问答失败（流式） - 知识库ID: {}", knowledgeBaseId, e);
            return Flux.error(new BusinessException("知识库问答失败", ErrorCode.SYSTEM_BUSY, e));
        }
    }

    /**
     * 构建消息列表（包含历史对话）
     */
    private List<ChatMessage> buildMessages(KnowledgeBaseQARequest request, String memoryContext) {
        List<ChatMessage> messages = new ArrayList<>();

        String systemPrompt = SkillLoader.loadSkill("knowledge_base/qa_system_prompt");
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            // 使用 fallback
            String fallback = SkillLoader.loadSkill("knowledge_base/qa_system_prompt_fallback");
            if (fallback != null && !fallback.trim().isEmpty()) {
                systemPrompt = fallback;
                // 追加 Markdown 格式要求
                String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
                if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
                    systemPrompt += "\n\n" + markdownFormat.trim();
                }
            } else {
                systemPrompt = SkillLoader.loadSkill("knowledge_base/qa_system_prompt_default");
                // 追加 Markdown 格式要求
                String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
                if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
                    systemPrompt += "\n\n" + markdownFormat.trim();
                }
            }
        } else {
            // 如果主提示词存在，检查是否已包含 Markdown 格式要求，如果没有则追加
            if (!systemPrompt.contains("Markdown格式") && !systemPrompt.contains("代码块格式")) {
                String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
                if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
                    systemPrompt += "\n\n" + markdownFormat.trim();
                }
            }
        }
        StringBuilder systemMessage = new StringBuilder(systemPrompt);
        if (memoryContext != null && !memoryContext.trim().isEmpty()) {
            systemMessage.append("\n\n").append(memoryContext);
        }
        String conversationSummary = getConversationSummaryForRequest(request);
        if (conversationSummary != null && !conversationSummary.trim().isEmpty()) {
            systemMessage.append("\n\n【当前会话摘要】\n")
                    .append(conversationSummary.trim())
                    .append("\n请把以上摘要作为当前会话的历史上下文；若摘要与最近对话冲突，以最近对话为准。");
        }
        messages.add(SystemMessage.from(systemMessage.toString()));

        // 添加历史对话
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (KnowledgeBaseQARequest.Message historyMsg : request.getHistory()) {
                if ("user".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(UserMessage.from(historyMsg.getContent()));
                } else if ("assistant".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(AiMessage.from(historyMsg.getContent()));
                }
            }
        }

        // 添加当前问题
        messages.add(UserMessage.from(request.getQuestion()));

        return messages;
    }

    private String getConversationSummaryForRequest(KnowledgeBaseQARequest request) {
        if (request == null || request.getUserId() == null) {
            return "";
        }
        Long conversationId = ConversationIdUtil.parseConversationId(request.getConversationId(), logger);
        if (conversationId == null) {
            return "";
        }
        try {
            return chatHistoryService.getConversationSummary(conversationId, request.getUserId(), false);
        } catch (Exception e) {
            logger.debug("读取会话摘要失败 - conversationId={}", conversationId, e);
            return "";
        }
    }

    private void appendConversationSummary(StringBuilder systemMessage, KnowledgeBaseQARequest request) {
        String conversationSummary = getConversationSummaryForRequest(request);
        if (conversationSummary == null || conversationSummary.trim().isEmpty()) {
            return;
        }
        systemMessage.append("【当前会话摘要】\n")
                .append(conversationSummary.trim())
                .append("\n请把以上摘要作为当前会话的历史上下文；若摘要与最近对话冲突，以最近对话为准。\n\n");
    }

    /**
     * 使用历史对话生成答案（非流式）
     */
    private Response<AiMessage> generateAnswerWithHistory(List<ChatMessage> messages, Long knowledgeBaseId,
            KnowledgeBaseQARequest request, String memoryContext,
            List<RagRetrievalService.RetrievalResult> retrievalResults) {
        // 构建包含检索内容的系统消息
        if (!retrievalResults.isEmpty()) {
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("基于以下知识库内容回答问题：\n\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                contextBuilder.append("文档片段 ").append(i + 1).append(":\n");
                contextBuilder.append(retrievalResults.get(i).getText()).append("\n\n");
            }

            // 替换系统消息
            StringBuilder sys = new StringBuilder();
            if (memoryContext != null && !memoryContext.trim().isEmpty()) {
                sys.append(memoryContext).append("\n\n");
            }
            appendConversationSummary(sys, request);

            // 使用模板构建检索上下文
            Map<String, String> variables = new HashMap<>();
            variables.put("retrievalContext", contextBuilder.toString());
            String template = SkillLoader.loadSkillWithTemplate("knowledge_base/retrieval_context_template", variables);
            if (template != null && !template.trim().isEmpty()) {
                sys.append(template);
            } else {
                // Fallback
                sys.append(contextBuilder.toString()).append(
                        "如果知识库中没有相关信息，请明确说明无法回答。");
            }

            // 追加 Markdown 格式要求
            String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
            if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
                sys.append("\n\n").append(markdownFormat.trim());
            }

            messages.set(0, SystemMessage.from(sys.toString()));
        }

        // 获取问答模型（优先使用请求中的modelId，否则使用默认的RAG模型）
        Long modelId = request.getModelId();
        QAModel qaModel;
        try {
            if (modelId != null) {
                // 使用指定的模型
                qaModel = modelConfigService.getQAModelById(modelId);
            } else {
                // 使用默认的RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
        } catch (Exception e) {
            logger.error("获取问答模型失败，使用默认模型 - 知识库ID: {}, modelId: {}", knowledgeBaseId, modelId, e);
            qaModel = modelConfigService.getDefaultQAModelForRAG();
        }

        // 创建模型实例（会话ID由AOP切面自动设置）
        ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);

        // 调用LLM生成答案
        return chatLanguageModel.generate(messages); // 返回Response对象，以便提取token信息
    }

    /**
     * 使用历史对话生成流式答案
     */
    private Flux<KnowledgeBaseQAResponse> generateStreamAnswerWithHistory(
            List<ChatMessage> messages, Long knowledgeBaseId,
            KnowledgeBaseQARequest request, List<KnowledgeBaseQAResponse.SourceDocument> sources, Long userId,
            String memoryContext, List<RagRetrievalService.RetrievalResult> retrievalResults) {
        // 构建包含检索内容的系统消息
        if (!retrievalResults.isEmpty()) {
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("基于以下知识库内容回答问题：\n\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                contextBuilder.append("文档片段 ").append(i + 1).append(":\n");
                contextBuilder.append(retrievalResults.get(i).getText()).append("\n\n");
            }

            // 构建完整的系统消息
            StringBuilder sys = new StringBuilder();
            if (memoryContext != null && !memoryContext.trim().isEmpty()) {
                sys.append(memoryContext).append("\n\n");
            }
            appendConversationSummary(sys, request);

            // 使用模板构建检索上下文
            Map<String, String> variables = new HashMap<>();
            variables.put("retrievalContext", contextBuilder.toString());
            String template = SkillLoader.loadSkillWithTemplate("knowledge_base/retrieval_context_template", variables);
            if (template != null && !template.trim().isEmpty()) {
                sys.append(template);
            } else {
                // Fallback
                sys.append(contextBuilder.toString()).append(
                        "如果知识库中没有相关信息，请明确说明无法回答。");
            }

            // 追加 Markdown 格式要求
            String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
            if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
                sys.append("\n\n").append(markdownFormat.trim());
            }
            String fullSystemMessage = sys.toString();

            // 压缩系统消息中的文档内容
            String compressedSystemMessage = contextCompressionService.compressDocumentContent(
                    fullSystemMessage, ragConfig.getMaxSystemMessageLength());

            // 替换系统消息
            messages.set(0, SystemMessage.from(compressedSystemMessage));
        }

        // 获取问答模型（优先使用请求中的modelId，否则使用默认的RAG模型）
        Long requestModelId = request.getModelId();
        QAModel qaModel;
        try {
            if (requestModelId != null) {
                // 使用指定的模型
                qaModel = modelConfigService.getQAModelById(requestModelId);
            } else {
                // 使用默认的RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
        } catch (Exception e) {
            logger.error("获取问答模型失败，使用默认模型 - 知识库ID: {}, modelId: {}", knowledgeBaseId, requestModelId, e);
            qaModel = modelConfigService.getDefaultQAModelForRAG();
        }

        // 保存qaModel的ID，用于后续保存消息
        final Long finalModelId = qaModel != null ? qaModel.getId() : null;

        if (qaModel == null) {
            logger.error("无法获取问答模型，无法继续生成答案");
            return Flux.error(new BusinessException("无法获取问答模型", ErrorCode.MODEL_NOT_FOUND));
        }

        // 在流式响应开始前，先创建或获取会话（这样可以在第一个数据包就返回 conversationId）
        final java.util.concurrent.atomic.AtomicReference<Long> conversationIdRef = new java.util.concurrent.atomic.AtomicReference<>(
                null);
        if (userId != null) {
            try {
                Long requestConversationId = ConversationIdUtil.parseConversationId(request.getConversationId(), logger);
                Long conversationId = chatHistoryService.getOrCreateConversation(
                        userId, requestConversationId, 2, null, knowledgeBaseId, request.getQuestion());
                conversationIdRef.set(conversationId);
                logger.info("流式响应开始 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}",
                        requestConversationId, conversationId);
                // 先保存用户消息
                chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                
                // 更新ThreadLocal中的会话ID（因为AOP在方法开始时执行，此时会话可能还未创建）
                modelLanguageModelFactory.setConversationId(conversationId.toString());
            } catch (Exception e) {
                logger.error("创建会话失败（流式）", e);
                // 不抛出异常，避免影响主流程
            }
        }

        // 创建流式模型实例（会话ID由AOP切面自动设置）
        StreamingChatLanguageModel streamingModel = modelLanguageModelFactory.createStreamingChatLanguageModel(qaModel);

        // 调用流式LLM生成答案
        logger.info("开始调用流式LLM生成答案 - 知识库ID: {}, 模型ID: {}, 模型名称: {}, 消息数量: {}",
                knowledgeBaseId, qaModel.getId(), qaModel.getName(), messages.size());
        Flux<String> tokenFlux = streamingModel.generateStream(messages)
                .doOnSubscribe(subscription -> {
                    logger.info("开始订阅token流");
                })
                .doOnNext(token -> {
                    logger.debug("收到token: {}", token.length() > 50 ? token.substring(0, 50) + "..." : token);
                })
                .doOnComplete(() -> {
                    logger.info("token流完成");
                })
                .doOnError(error -> {
                    logger.error("token流发生错误", error);
                });

        // 使用scan累积答案，并保存最后一个完整答案
        final java.util.concurrent.atomic.AtomicReference<String> lastAnswer = new java.util.concurrent.atomic.AtomicReference<>(
                "");

        return tokenFlux
                .scan("", (accumulated, token) -> {
                    String newAccumulated = accumulated + token;
                    lastAnswer.set(newAccumulated); // 保存最新的完整答案
                    logger.debug("累积答案，当前长度: {}", newAccumulated.length());
                    return newAccumulated;
                })
                .skip(1) // 跳过第一个空字符串（scan 的初始值）
                .map(fullAnswer -> {
                    KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                    response.setAnswer(fullAnswer);
                    response.setSources(sources);
                    response.setFinished(false);
                    // 在流式响应过程中，也包含 conversationId，这样前端可以立即更新
                    response.setConversationId(
                            conversationIdRef.get() != null ? conversationIdRef.get().toString() : null);
                    return response;
                })
                .doOnNext(response -> {
                    logger.debug("发送流式响应，答案长度: {}", response.getAnswer().length());
                })
                .switchIfEmpty(Flux.defer(() -> {
                    // 如果没有收到任何token，发送一个提示
                    logger.warn("没有收到任何token，发送空响应");
                    KnowledgeBaseQAResponse emptyResponse = new KnowledgeBaseQAResponse();
                    emptyResponse.setAnswer("未收到LLM响应，请检查日志");
                    emptyResponse.setSources(sources);
                    emptyResponse.setFinished(true);
                    return Flux.just(emptyResponse);
                }))
                .concatWith(Flux.defer(() -> {
                    // 发送最终响应（包含完整答案和sources）
                    String finalAnswer = lastAnswer.get();
                    logger.info("发送最终响应标记，完整答案长度: {}", finalAnswer.length());

                    // 保存助手消息（会话已在开始时创建）
                    Long conversationId = conversationIdRef.get();
                    if (userId != null && conversationId != null && !finalAnswer.trim().isEmpty()) {
                        try {
                            // 流式响应通常无法直接获取token使用信息
                            // 这里先保存modelId，token信息留空（后续可以通过其他方式补充）
                            chatHistoryService.saveMessage(conversationId, "assistant", finalAnswer,
                                    finalModelId, null, null, null);
                            logger.info("流式响应完成 - 保存助手消息到会话: {}, 模型ID: {}", conversationId, finalModelId);
                            try {
                                userMemoryService.updateMemoryAsync(userId, request.getQuestion(), finalAnswer,
                                        finalModelId, conversationId, "knowledge_base", knowledgeBaseId);
                            } catch (Exception e) {
                                logger.debug("触发异步记忆更新失败（知识库流式）", e);
                            }
                        } catch (Exception e) {
                            logger.error("保存助手消息失败（流式）", e);
                            // 不抛出异常，避免影响主流程
                        }
                    }

                    KnowledgeBaseQAResponse finalResponse = new KnowledgeBaseQAResponse();
                    finalResponse.setAnswer(finalAnswer); // 包含完整的答案，而不是空字符串
                    finalResponse.setSources(sources);
                    finalResponse.setFinished(true);
                    finalResponse.setConversationId(conversationId != null ? conversationId.toString() : null);
                    return Flux.just(finalResponse);
                }))
                .onErrorResume(error -> {
                    logger.error("流式问答失败", error);
                    KnowledgeBaseQAResponse errorResponse = new KnowledgeBaseQAResponse();
                    errorResponse.setAnswer("系统繁忙，请稍后重试");
                    errorResponse.setSources(sources);
                    errorResponse.setFinished(true);
                    return Flux.just(errorResponse);
                });
    }

    /**
     * 验证用户是否有权限访问知识库
     * 
     * @param knowledgeBaseId 知识库ID
     * @param userId          用户ID
     * @param userRole        用户角色（1-管理员，0-普通用户），如果为null则按普通用户处理
     * @throws BusinessException 如果用户没有权限访问该知识库
     */
    private void validateKnowledgeBaseAccess(Long knowledgeBaseId, Long userId, Integer userRole) {
        if (userId == null) {
            throw new UnauthorizedException("用户未登录，无法访问知识库");
        }

        // 获取知识库信息
        com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp kb;
        try {
            kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        } catch (Exception e) {
            logger.error("获取知识库信息失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new BusinessException("知识库不存在或已删除", ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 检查知识库是否启用
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new BusinessException("知识库已被禁用，无法进行问答", ErrorCode.BAD_REQUEST);
        }

        // 权限检查：管理员可以看到所有知识库（但仍需检查用户可见性设置）
        if (userRole != null && userRole == 1) {
            // 管理员还需要检查用户可见性设置（如果设置了的话）
            if (!userKnowledgeBaseVisibilityService.hasAccess(userId, knowledgeBaseId)) {
                throw new BusinessException("您没有权限访问该知识库", ErrorCode.FORBIDDEN);
            }
            logger.debug("管理员权限验证通过 - 用户ID: {}, 知识库ID: {}", userId, knowledgeBaseId);
            return;
        }

        // 普通用户权限检查
        boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
        boolean isOwner = userId.equals(kb.getCreatorId());

        // 普通用户只能访问：
        // 1. 公开的知识库（is_public = true）
        // 2. 自己创建的私有知识库（creator_id = userId AND is_public = false）
        if (isPublic || isOwner) {
            // 还需要检查用户可见性设置（如果设置了的话）
            if (!userKnowledgeBaseVisibilityService.hasAccess(userId, knowledgeBaseId)) {
                throw new BusinessException("您没有权限访问该知识库", ErrorCode.FORBIDDEN);
            }
            logger.debug("普通用户权限验证通过 - 用户ID: {}, 知识库ID: {}, 是否公开: {}, 是否创建者: {}",
                    userId, knowledgeBaseId, isPublic, isOwner);
        } else {
            // 私有知识库且不是创建者，直接拒绝
            throw new BusinessException("您没有权限访问该私有知识库", ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 估算Token使用量（基于内容长度）
     * 这是一个简单的估算方法，实际token数可能因模型而异
     * 一般规则：中文约1.5字符=1token，英文约4字符=1token
     */
    private Long[] estimateTokenUsage(String question, String answer) {
        long promptTokens = 0;
        long completionTokens = 0;

        // 估算prompt tokens（问题内容）
        if (question != null && !question.isEmpty()) {
            long chineseChars = question.chars().filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF).count();
            long otherChars = question.length() - chineseChars;
            promptTokens = (long) (chineseChars / 1.5 + (double) otherChars / 4);
        }

        // 估算completion tokens（回答内容）
        if (answer != null && !answer.isEmpty()) {
            long chineseChars = answer.chars().filter(ch -> ch >= 0x4E00 && ch <= 0x9FFF).count();
            long otherChars = answer.length() - chineseChars;
            completionTokens = (long) (chineseChars / 1.5 + (double) otherChars / 4);
        }

        long totalTokens = promptTokens + completionTokens;

        logger.debug("估算Token使用量 - Prompt: {}, Completion: {}, Total: {}",
                promptTokens, completionTokens, totalTokens);

        return new Long[] { promptTokens, completionTokens, totalTokens };
    }

    private TraceHandle startBusinessTrace(String traceSource, Long knowledgeBaseId, Long userId,
            KnowledgeBaseQARequest request, boolean stream) {
        try {
            TraceStartRequest startRequest = new TraceStartRequest();
            startRequest.setTraceSource(traceSource);
            startRequest.setConversationId(request != null ? request.getConversationId() : null);
            startRequest.setUserId(userId);
            startRequest.setRequestType(stream ? "qa_stream" : "qa");
            startRequest.setBusinessId(knowledgeBaseId);
            startRequest.setRequestSummary(request == null ? null : request.getQuestion());
            TraceHandle handle = traceFacade.start(startRequest);
            if (handle != null && handle.getTraceId() != null && !handle.getTraceId().isBlank()) {
                modelLanguageModelFactory.setTraceId(handle.getTraceId());
                modelLanguageModelFactory.markBusinessTraceStarted();
            }
            return handle;
        } catch (Exception e) {
            logger.debug("启动业务追踪失败，降级继续执行业务", e);
            return null;
        }
    }
}
