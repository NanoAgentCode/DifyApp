package com.github.app.dify.chat.service.impl;

import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.StreamingChatLanguageModel;
import com.github.app.dify.mcp.service.McpBrowserSearchService;
import com.github.app.dify.mcp.service.McpTimeService;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.chat.service.ChatService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.util.TokenEstimator;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import com.github.app.dify.memory.service.UserMemoryService;
import com.github.app.dify.ops.observability.annotation.LLMTrace;
import com.github.app.dify.system.util.SkillLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能问答服务实现（直接对话，不使用知识库）
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatServiceImpl.class);

    @Autowired
    private QAModelRepository qaModelRepository;

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired
    private ContextCompressionService contextCompressionService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private McpBrowserSearchService mcpBrowserSearchService;

    @Autowired
    private McpTimeService mcpTimeService;

    @Autowired
    private UserMemoryService userMemoryService;

    @Override
    @LLMTrace(
            traceSource = "Chat",
            conversationIdParam = "request.conversationId",
            extractFromReturn = true
    )
    public ChatResponse chat(ChatRequest request, Long userId) {
        try {
            // 获取模型配置
            QAModel qaModel = getQAModel(request.getModelId());
            if (qaModel == null) {
                throw new IllegalStateException("未找到可用的问答模型，请先配置模型");
            }

            logger.info("使用问答模型: {} (ID: {})", qaModel.getName(), qaModel.getId());

            // 创建模型实例（会话ID由AOP切面自动设置）
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);

            // 如果启用了MCP支持，直接使用浏览器检索（不再进行检测）
            String browserSearchContext = "";
            if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
                logger.debug("MCP支持已开启，直接启用浏览器检索 - 查询: {}", request.getQuestion());
                try {
                    List<McpBrowserSearchService.SearchResult> searchResults = mcpBrowserSearchService
                            .search(request.getQuestion(), 5);
                    if (searchResults != null && !searchResults.isEmpty()) {
                        browserSearchContext = mcpBrowserSearchService.formatSearchResultsForContext(searchResults);
                        logger.info("浏览器检索完成 - 找到 {} 个结果", searchResults.size());
                        // 记录检索结果详情（仅记录前200字符，避免日志过长）
                        logger.trace("检索结果预览: {}",
                                browserSearchContext.length() > 200 ? browserSearchContext.substring(0, 200) + "..."
                                        : browserSearchContext);
                    } else {
                        logger.debug("浏览器检索未找到结果 - 查询: {}", request.getQuestion());
                        // 即使没有找到结果，也告知LLM已尝试检索
                        browserSearchContext = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                                "问题：" + request.getQuestion() + "\n\n" +
                                "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
                    }
                } catch (Exception e) {
                    logger.error("浏览器检索失败，继续使用原始问题 - 查询: {}", request.getQuestion(), e);
                    // 不抛出异常，继续使用原始问题
                }
            } else {
                logger.debug("MCP支持已关闭，跳过浏览器检索");
            }

            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request, browserSearchContext, qaModel, userId);

            // 记录历史对话信息
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                logger.debug("使用历史对话，历史消息数量: {}", request.getHistory().size());
            }
            logger.trace("构建的消息列表大小: {}", messages.size());

            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
            messages = contextCompressionService.compressContext(messages, kbRequest);
            logger.debug("压缩后的消息列表大小: {}", messages.size());

            // 设置图片数据到ThreadLocal（用于多模态支持）
            try {
                if (request.getImages() != null && !request.getImages().isEmpty()) {
                    modelLanguageModelFactory.setImageData(request.getImages());
                    logger.debug("已设置图片数据到模型工厂，图片数量: {}", request.getImages().size());
                }

                // 调用LLM生成答案
                Response<AiMessage> response = chatLanguageModel.generate(messages);
                String answer = response.content().text();

                // 提取token使用信息
                Long promptTokens = null;
                Long completionTokens = null;
                Long totalTokens = null;

                // 尝试从Response中获取token使用信息
                logger.debug("开始获取Token使用信息...");
                try {
                    // langchain4j的Response可能包含tokenUsage信息
                    dev.langchain4j.model.output.TokenUsage tokenUsage = response.tokenUsage();
                    logger.debug("调用tokenUsage()方法成功，结果: {}", tokenUsage != null ? "非null" : "null");

                    if (tokenUsage != null) {
                        promptTokens = tokenUsage.inputTokenCount() != null ? (long) tokenUsage.inputTokenCount()
                                : null;
                        completionTokens = tokenUsage.outputTokenCount() != null ? (long) tokenUsage.outputTokenCount()
                                : null;
                        totalTokens = tokenUsage.totalTokenCount() != null ? (long) tokenUsage.totalTokenCount() : null;
                        logger.info("✓ 从Response获取到Token使用信息 - Prompt: {}, Completion: {}, Total: {}",
                                promptTokens, completionTokens, totalTokens);
                    } else {
                        logger.warn("Response对象中tokenUsage为null，将使用估算方法");
                        // 如果无法获取token信息，尝试基于内容估算
                        Long[] estimated = TokenEstimator.estimateTokenUsage(
                                messages.toArray(new dev.langchain4j.data.message.ChatMessage[0]), answer);
                        promptTokens = estimated[0];
                        completionTokens = estimated[1];
                        totalTokens = estimated[2];
                        logger.info("✓ 使用估算方法获取Token - Prompt: {}, Completion: {}, Total: {}",
                                promptTokens, completionTokens, totalTokens);
                    }
                } catch (NoSuchMethodError e) {
                    logger.warn("Response对象不包含tokenUsage方法，将使用估算方法: {}", e.getMessage());
                    // 如果方法不存在，尝试基于内容估算
                    Long[] estimated = TokenEstimator.estimateTokenUsage(
                            messages.toArray(new dev.langchain4j.data.message.ChatMessage[0]), answer);
                    promptTokens = estimated[0];
                    completionTokens = estimated[1];
                    totalTokens = estimated[2];
                    logger.info("✓ 使用估算方法获取Token - Prompt: {}, Completion: {}, Total: {}",
                            promptTokens, completionTokens, totalTokens);
                } catch (Exception e) {
                    logger.warn("获取Token使用信息时发生异常: {}，将使用估算方法", e.getMessage());
                    logger.debug("Token获取异常详情", e);
                    // 尝试基于内容估算
                    Long[] estimated = TokenEstimator.estimateTokenUsage(
                            messages.toArray(new dev.langchain4j.data.message.ChatMessage[0]), answer);
                    promptTokens = estimated[0];
                    completionTokens = estimated[1];
                    totalTokens = estimated[2];
                    logger.info("✓ 使用估算方法获取Token - Prompt: {}, Completion: {}, Total: {}",
                            promptTokens, completionTokens, totalTokens);
                }

                logger.info("最终Token值 - Prompt: {}, Completion: {}, Total: {}",
                        promptTokens, completionTokens, totalTokens);

                // 构建响应（包含保存历史记录）
                return buildChatResponse(answer, request, userId, null, qaModel.getId(),
                        promptTokens, completionTokens, totalTokens);
            } finally {
                // 清除ThreadLocal数据
                modelLanguageModelFactory.clearImageData();
            }

        } catch (Exception e) {
            logger.error("智能问答失败", e);
            throw new BusinessException("智能问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e);
        }
    }

    /**
     * 构建聊天响应
     */
    private ChatResponse buildChatResponse(String answer, ChatRequest request, Long userId, Long conversationId,
            Long modelId, Long promptTokens, Long completionTokens, Long totalTokens) {
        // 保存历史记录
        if (userId != null && conversationId == null) {
            try {
                Long requestConversationId = null;
                if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                    try {
                        requestConversationId = Long.parseLong(request.getConversationId());
                    } catch (NumberFormatException e) {
                        logger.warn("无效的conversationId: {}", request.getConversationId());
                    }
                }
                conversationId = chatHistoryService.getOrCreateConversation(
                        userId, requestConversationId, 1, null, null, request.getQuestion());
                logger.info("非流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}",
                        requestConversationId, conversationId);
                // 保存用户消息（不需要token信息）
                chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                // 保存助手消息（带token信息）
                logger.info("准备保存助手消息 - conversationId: {}, modelId: {}, tokens: {}/{}/{}",
                        conversationId, modelId, promptTokens, completionTokens, totalTokens);
                chatHistoryService.saveMessage(conversationId, "assistant", answer,
                        modelId, promptTokens, completionTokens, totalTokens);
                logger.info("✓ 助手消息已保存");
                try {
                    userMemoryService.updateMemoryAsync(userId, request.getQuestion(), answer, modelId, conversationId,
                            "chat", null);
                } catch (Exception e) {
                    logger.debug("触发异步记忆更新失败", e);
                }
            } catch (Exception e) {
                logger.error("保存历史记录失败", e);
                // 不抛出异常，避免影响主流程
            }
        }

        // 构建响应
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setAnswer(answer);
        chatResponse.setConversationId(conversationId);

        logger.info("智能问答完成 - 问题: {}", request.getQuestion());

        return chatResponse;
    }

    @Override
    @LLMTrace(
            traceSource = "Chat",
            conversationIdParam = "request.conversationId"
    )
    public Flux<ChatResponse> chatStream(ChatRequest request, Long userId) {
        try {
            // 获取模型配置
            QAModel qaModel = getQAModel(request.getModelId());
            if (qaModel == null) {
                return Flux.error(new IllegalStateException("未找到可用的问答模型，请先配置模型"));
            }

            logger.info("使用问答模型（流式）: {} (ID: {})", qaModel.getName(), qaModel.getId());

            // 在流式响应开始前，先创建或获取会话（这样可以在第一个数据包就返回 conversationId）
            final AtomicReference<Long> conversationIdRef = new AtomicReference<>(null);
            if (userId != null) {
                try {
                    Long requestConversationId = null;
                    if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                        try {
                            requestConversationId = Long.parseLong(request.getConversationId());
                        } catch (NumberFormatException e) {
                            logger.warn("无效的conversationId: {}", request.getConversationId());
                        }
                    }
                    Long conversationId = chatHistoryService.getOrCreateConversation(
                            userId, requestConversationId, 1, null, null, request.getQuestion());
                    conversationIdRef.set(conversationId);
                    logger.info("流式响应开始 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}",
                            requestConversationId, conversationId);
                    // 先保存用户消息（不需要token信息）
                    chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                    
                    // 更新ThreadLocal中的会话ID（因为AOP在方法开始时执行，此时会话可能还未创建）
                    modelLanguageModelFactory.setConversationId(conversationId.toString());
                } catch (Exception e) {
                    logger.error("创建会话失败（流式）", e);
                    // 不抛出异常，避免影响主流程
                }
            }

            // 创建流式模型实例（会话ID由AOP切面自动设置）
            StreamingChatLanguageModel streamingChatLanguageModel = modelLanguageModelFactory
                    .createStreamingChatLanguageModel(qaModel);

            // 如果启用了MCP支持，直接使用浏览器检索（不再进行检测）
            String browserSearchContext = "";
            if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
                logger.info("MCP支持已开启（流式），直接启用浏览器检索 - 查询: {}", request.getQuestion());
                try {
                    List<McpBrowserSearchService.SearchResult> searchResults = mcpBrowserSearchService
                            .search(request.getQuestion(), 5);
                    if (searchResults != null && !searchResults.isEmpty()) {
                        browserSearchContext = mcpBrowserSearchService.formatSearchResultsForContext(searchResults);
                        logger.info("浏览器检索完成（流式） - 找到 {} 个结果，检索内容长度: {} 字符",
                                searchResults.size(), browserSearchContext.length());
                        // 记录检索结果详情（仅记录前200字符，避免日志过长）
                        logger.debug("检索结果预览（流式）: {}",
                                browserSearchContext.length() > 200 ? browserSearchContext.substring(0, 200) + "..."
                                        : browserSearchContext);
                    } else {
                        logger.warn("浏览器检索未找到结果（流式） - 查询: {}", request.getQuestion());
                        // 即使没有找到结果，也告知LLM已尝试检索
                        browserSearchContext = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                                "问题：" + request.getQuestion() + "\n\n" +
                                "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
                    }
                } catch (Exception e) {
                    logger.error("浏览器检索失败（流式），继续使用原始问题 - 查询: {}", request.getQuestion(), e);
                    // 不抛出异常，继续使用原始问题
                }
            } else {
                logger.info("MCP支持已关闭（流式），跳过浏览器检索");
            }

            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request, browserSearchContext, qaModel, userId);

            // 应用上下文压缩策略（转换为KnowledgeBaseQARequest格式以复用压缩逻辑）
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest = convertToKBQARequest(request);
            final List<ChatMessage> finalMessages = contextCompressionService.compressContext(messages, kbRequest);
            logger.debug("压缩后的消息列表大小（流式）: {}", finalMessages.size());

            // 设置图片数据到ThreadLocal（用于多模态支持）
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                modelLanguageModelFactory.setImageData(request.getImages());
                logger.debug("已设置图片数据到模型工厂（流式），图片数量: {}", request.getImages().size());
            }

            // 调用流式LLM生成答案
            logger.info("开始调用流式LLM生成答案 - 消息数量: {}", finalMessages.size());
            Flux<String> tokenFlux = streamingChatLanguageModel.generateStream(finalMessages)
                    .doFinally(signalType -> {
                        // 清除ThreadLocal数据
                        modelLanguageModelFactory.clearImageData();
                    })
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
            final AtomicReference<String> lastAnswer = new AtomicReference<>("");

            return tokenFlux
                    .scan("", (accumulated, token) -> {
                        String newAccumulated = accumulated + token;
                        lastAnswer.set(newAccumulated);
                        logger.debug("累积答案，当前长度: {}", newAccumulated.length());
                        return newAccumulated;
                    })
                    .skip(1) // 跳过第一个空字符串（scan 的初始值）
                    .map(fullAnswer -> {
                        ChatResponse response = new ChatResponse();
                        response.setAnswer(fullAnswer);
                        response.setFinished(false);
                        // 在流式响应过程中，也包含 conversationId，这样前端可以立即更新
                        response.setConversationId(conversationIdRef.get());
                        return response;
                    })
                    .doOnNext(response -> {
                        logger.debug("发送流式响应，答案长度: {}", response.getAnswer().length());
                    })
                    .switchIfEmpty(Flux.defer(() -> {
                        logger.warn("没有收到任何token，发送空响应");
                        ChatResponse emptyResponse = new ChatResponse();
                        emptyResponse.setAnswer("未收到LLM响应，请检查日志");
                        emptyResponse.setFinished(true);
                        return Flux.just(emptyResponse);
                    }))
                    .concatWith(Flux.defer(() -> {
                        String finalAnswer = lastAnswer.get();
                        logger.info("发送最终响应标记，完整答案长度: {}", finalAnswer.length());

                        // 保存助手消息（会话已在开始时创建）
                        Long conversationId = conversationIdRef.get();
                        if (userId != null && conversationId != null && !finalAnswer.trim().isEmpty()) {
                            try {
                                // 流式响应通常无法直接获取token使用信息，使用估算方法
                                logger.info("流式响应完成，开始估算Token使用量...");
                                Long[] estimated = TokenEstimator.estimateTokenUsage(
                                        finalMessages.toArray(new dev.langchain4j.data.message.ChatMessage[0]),
                                        finalAnswer);
                                Long promptTokens = estimated[0];
                                Long completionTokens = estimated[1];
                                Long totalTokens = estimated[2];
                                logger.info("流式响应Token估算结果 - Prompt: {}, Completion: {}, Total: {}",
                                        promptTokens, completionTokens, totalTokens);

                                chatHistoryService.saveMessage(conversationId, "assistant", finalAnswer,
                                        qaModel.getId(), promptTokens, completionTokens, totalTokens);
                                logger.info("✓ 流式响应完成 - 已保存助手消息到会话: {}, 模型ID: {}, Token: {}/{}/{}",
                                        conversationId, qaModel.getId(), promptTokens, completionTokens, totalTokens);
                                try {
                                    userMemoryService.updateMemoryAsync(userId, request.getQuestion(), finalAnswer,
                                            qaModel.getId(), conversationId, "chat", null);
                                } catch (Exception e) {
                                    logger.debug("触发异步记忆更新失败（流式）", e);
                                }
                            } catch (Exception e) {
                                logger.error("保存助手消息失败（流式）", e);
                                // 不抛出异常，避免影响主流程
                            }
                        }

                        ChatResponse finalResponse = new ChatResponse();
                        finalResponse.setAnswer(finalAnswer);
                        finalResponse.setFinished(true);
                        finalResponse.setConversationId(conversationId);
                        return Flux.just(finalResponse);
                    }))
                    .onErrorResume(error -> {
                        logger.error("流式问答失败", error);
                        ChatResponse errorResponse = new ChatResponse();
                        errorResponse.setAnswer("系统繁忙，请稍后重试");
                        errorResponse.setFinished(true);
                        return Flux.just(errorResponse);
                    });

        } catch (Exception e) {
            logger.error("智能问答失败（流式）", e);
            return Flux.error(new BusinessException("智能问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e));
        }
    }

    /**
     * 构建消息列表（包含历史对话和浏览器检索结果）
     */
    private List<ChatMessage> buildMessages(ChatRequest request, String browserSearchContext, QAModel qaModel,
            Long userId) {
        List<ChatMessage> messages = new ArrayList<>();

        // 检查模型是否支持视觉输入
        boolean supportsVision = qaModel != null &&
                Boolean.TRUE.equals(qaModel.getSupportsVision()) &&
                Boolean.TRUE.equals(qaModel.getSupportsMultimodal());

        String base = SkillLoader.loadSkill("chat/system_prompt");
        StringBuilder systemMessageBuilder = new StringBuilder();
        if (base != null && !base.trim().isEmpty()) {
            systemMessageBuilder.append(base.trim()).append("\n\n");
        } else {
            // 使用 fallback
            String fallback = SkillLoader.loadSkill("chat/system_prompt_fallback");
            if (fallback != null && !fallback.trim().isEmpty()) {
                systemMessageBuilder.append(fallback.trim()).append("\n\n");
            } else {
                systemMessageBuilder.append("你是一个专业的AI助手，能够回答各种问题，特别擅长编程和技术问题。\n\n");
            }
        }

        // 如果模型支持视觉输入，添加图片处理说明
        if (supportsVision) {
            String visionCapability = SkillLoader.loadSkill("chat/vision_capability");
            if (visionCapability != null && !visionCapability.trim().isEmpty()) {
                systemMessageBuilder.append(visionCapability.trim()).append("\n\n");
            } else {
                // Fallback
                systemMessageBuilder.append("【视觉能力说明】\n");
                systemMessageBuilder.append("你具备视觉理解能力，可以直接处理和分析用户上传的图片。当用户上传图片时，你应该：\n");
                systemMessageBuilder.append("1. 仔细观察图片中的所有内容，包括文字、图表、代码、界面元素等\n");
                systemMessageBuilder.append("2. 准确识别图片中的文字内容（包括代码、公式、表格等）\n");
                systemMessageBuilder.append("3. 理解图片的上下文和意图，提供准确、详细的分析\n");
                systemMessageBuilder.append("4. 对于代码截图，能够识别代码语言、逻辑结构，并提供专业的代码分析\n");
                systemMessageBuilder.append("5. 对于图表、流程图等，能够描述其结构和含义\n");
                systemMessageBuilder.append("6. 对于界面截图，能够识别UI元素、布局和功能\n");
                systemMessageBuilder.append("7. 如果图片中包含问题或需求，直接基于图片内容进行回答，无需用户额外描述\n");
                systemMessageBuilder.append("8. 回答时应该详细、准确，充分利用图片中的所有信息\n");
                systemMessageBuilder.append("9. 不要告诉用户你无法处理图片，因为你具备视觉理解能力，可以直接分析图片内容\n\n");
            }
        } else {
            // 模型不支持视觉输入，智能问答不支持图片处理
            String noVision = SkillLoader.loadSkill("chat/no_vision_capability");
            if (noVision != null && !noVision.trim().isEmpty()) {
                systemMessageBuilder.append(noVision.trim()).append("\n\n");
            } else {
                // Fallback
                systemMessageBuilder.append("【图片处理说明】\n");
                systemMessageBuilder.append("注意：智能问答功能不支持图片处理。如果用户上传了图片，系统会提示用户选择支持视觉输入的模型。\n");
                systemMessageBuilder.append("你只能处理文本输入，无法直接处理图片。\n\n");
            }
        }

        // 如果启用了MCP支持，添加时间信息和地理位置信息
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
            // 获取当前时间信息（用于时效性判断）
            String currentTimeInfo = mcpTimeService.getFormattedTimeInfo();
            McpTimeService.TimeInfo timeInfo = mcpTimeService.getCurrentTime();
            currentYear = timeInfo.getYear();
            systemMessageBuilder.append(currentTimeInfo);
        } else {
            // MCP支持关闭时，只提供基本的年份信息（不告知用户MCP已关闭）
            systemMessageBuilder.append("【当前时间信息】\n");
            systemMessageBuilder.append(String.format("当前年份：%d年\n", currentYear));
        }

        String memoryContext = userMemoryService.buildMemoryContext(userId, request.getQuestion(), "chat", null);
        if (memoryContext != null && !memoryContext.trim().isEmpty()) {
            systemMessageBuilder.append("\n").append(memoryContext).append("\n");
        }

        // 如果提供了浏览器检索结果，在系统消息中强调要使用检索结果
        if (browserSearchContext != null && !browserSearchContext.trim().isEmpty()) {
            Map<String, String> variables = new HashMap<>();
            variables.put("currentYear", String.valueOf(currentYear));
            String browserSearchSystem = SkillLoader.loadSkillWithTemplate("chat/browser_search_system", variables);
            if (browserSearchSystem != null && !browserSearchSystem.trim().isEmpty()) {
                systemMessageBuilder.append("\n\n").append(browserSearchSystem.trim());
            } else {
                // Fallback
                systemMessageBuilder.append("\n\n【重要提示】当用户问题中包含网络搜索结果时，你必须：");
                systemMessageBuilder.append("\n1. 优先使用搜索结果中的信息来回答问题");
                systemMessageBuilder.append("\n2. 在回答中明确引用搜索结果中的内容，并标注来源链接");
                systemMessageBuilder.append("\n3. 当前年份是").append(currentYear).append("年，请根据信息的时效性自行判断是否需要提醒用户信息可能已过期");
                systemMessageBuilder.append("\n4. 如果搜索结果与问题相关，必须基于搜索结果来回答，不要仅依赖你的训练数据");
                systemMessageBuilder.append("\n5. 如果搜索结果与问题不相关，可以结合你的知识来回答，但要说明信息来源");
            }
        }

        // 加载 Markdown 格式要求
        String markdownFormat = SkillLoader.loadSkill("common/markdown_format");
        if (markdownFormat != null && !markdownFormat.trim().isEmpty()) {
            systemMessageBuilder.append("\n\n").append(markdownFormat.trim());
        } else {
            // Fallback：如果文件不存在，使用硬编码（保持向后兼容）
            systemMessageBuilder.append("""
                    
                    
                    重要：请使用Markdown格式来组织你的回答，包括：
                    - 使用标题（#、##、###）来组织内容结构
                    - 使用列表（-、*、1.）来列举要点
                    - 使用代码块（```）来展示代码或技术内容
                    - 使用**粗体**和*斜体*来强调重要信息
                    - 使用表格来展示结构化数据
                    
                    【关键要求】代码块格式（必须严格遵守）：
                    1. 所有代码块必须包含语言标识符，格式为：```语言标识符
                    代码内容
                    ```
                    2. 语言标识符示例：
                       - JavaScript代码：```javascript
                    代码
                    ```
                       - Python代码：```python
                    代码
                    ```
                       - Java代码：```java
                    代码
                    ```
                       - TypeScript代码：```typescript
                    代码
                    ```
                       - Go代码：```go
                    代码
                    ```
                       - Rust代码：```rust
                    代码
                    ```
                       - C/C++代码：```cpp
                    代码
                    ``` 或 ```c
                    代码
                    ```
                       - C#代码：```csharp
                    代码
                    ```
                       - PHP代码：```php
                    代码
                    ```
                       - Ruby代码：```ruby
                    代码
                    ```
                       - Swift代码：```swift
                    代码
                    ```
                       - Kotlin代码：```kotlin
                    代码
                    ```
                       - SQL代码：```sql
                    代码
                    ```
                       - HTML代码：```html
                    代码
                    ```
                       - CSS代码：```css
                    代码
                    ```
                       - JSON代码：```json
                    代码
                    ```
                       - XML代码：```xml
                    代码
                    ```
                       - YAML代码：```yaml
                    代码
                    ```
                       - Bash/Shell代码：```bash
                    代码
                    ``` 或 ```shell
                    代码
                    ```
                    3. 绝对禁止使用没有语言标识符的代码块（如 ```
                    代码
                    ```），这会导致代码无法正确高亮显示
                    4. 在流式响应中，生成代码块时必须在第一行就包含完整的 ```语言标识符，例如：```javascript
                    5. 代码块中的代码应该完整、可运行，并包含必要的注释
                    6. 如果用户输入包含代码，请确保在回答中正确使用带语言标识符的代码块格式展示
                    
                    【关键要求】数学公式格式（必须严格遵守）：
                    1. 所有数学公式必须使用LaTeX格式编写，不要使用占位符或省略公式内容
                    2. 行内公式使用 $...$ 格式，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$
                    3. 块级公式使用 $$...$$ 格式，例如：
                       $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$
                    4. 也可以使用 [...] 格式表示块级公式，例如：
                       [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]
                    5. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整的LaTeX公式
                    6. 公式中的特殊字符需要使用反斜杠转义，例如：\\frac{分子}{分母}、\\sqrt{内容}、\\sum_{i=1}^{n} 等
                    7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符""");
        }

        // 添加系统消息
        messages.add(SystemMessage.from(systemMessageBuilder.toString()));

        // 添加历史对话
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (ChatRequest.Message historyMsg : request.getHistory()) {
                if ("user".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(UserMessage.from(historyMsg.getContent()));
                } else if ("assistant".equalsIgnoreCase(historyMsg.getRole())) {
                    messages.add(AiMessage.from(historyMsg.getContent()));
                }
            }
        }

        // 构建用户消息：如果有检索结果，将检索结果和问题一起发送
        String userMessageContent;
        if (Boolean.TRUE.equals(request.getEnableBrowserSearch())) {
            // MCP支持已开启
            if (browserSearchContext != null && !browserSearchContext.trim().isEmpty()) {
                // 使用模板构建用户消息
                Map<String, String> variables = new HashMap<>();
                variables.put("question", request.getQuestion());
                variables.put("currentYear", String.valueOf(currentYear));
                String template = SkillLoader.loadSkillWithTemplate("chat/browser_search_user_template", variables);
                if (template != null && !template.trim().isEmpty()) {
                    userMessageContent = browserSearchContext + "\n\n---\n\n" + template;
                } else {
                    // Fallback
                    userMessageContent = browserSearchContext +
                            "\n\n---\n\n" +
                            "基于以上网络搜索结果，请回答以下问题：\n" +
                            request.getQuestion() +
                            "\n\n【重要要求】\n" +
                            "1. 必须优先使用上述搜索结果中的信息来回答问题\n" +
                            "2. 如果搜索结果中包含相关信息，必须明确引用并标注来源链接\n" +
                            "3. 当前年份是" + currentYear + "年，请根据信息的时效性自行判断是否需要提醒用户信息可能已过期\n" +
                            "4. 如果搜索结果与问题不相关，请明确说明\"未在搜索结果中找到相关信息\"，然后可以结合你的知识回答\n" +
                            "5. 绝对不要声称搜索结果包含信息，如果搜索结果中没有相关内容，请明确说明";
                }
            } else {
                // 如果启用了MCP支持但没有找到结果，明确告知LLM
                Map<String, String> variables = new HashMap<>();
                variables.put("question", request.getQuestion());
                String template = SkillLoader.loadSkillWithTemplate("chat/browser_search_no_results", variables);
                if (template != null && !template.trim().isEmpty()) {
                    userMessageContent = template;
                } else {
                    // Fallback
                    userMessageContent = "【网络搜索提示】已启用MCP支持（浏览器检索功能），但未找到与问题相关的搜索结果。\n\n" +
                            "问题：" + request.getQuestion() + "\n\n" +
                            "请基于你的知识来回答这个问题。如果问题涉及实时信息或最新动态，请说明需要访问相关网站获取最新信息。";
                }
            }
        } else {
            // MCP支持已关闭，直接使用原始问题
            userMessageContent = request.getQuestion();
        }

        // 确保消息内容不为空
        if (userMessageContent == null || userMessageContent.trim().isEmpty()) {
            userMessageContent = "请帮我分析这些内容。";
            logger.warn("用户问题为空，使用默认问题");
        }

        // 添加当前问题（包含检索结果）
        messages.add(UserMessage.from(userMessageContent));

        return messages;
    }

    /**
     * 将ChatRequest转换为KnowledgeBaseQARequest（用于上下文压缩）
     */
    private com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest convertToKBQARequest(ChatRequest request) {
        com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest kbRequest = new com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest();
        kbRequest.setQuestion(request.getQuestion());
        kbRequest.setHistory(convertHistory(request.getHistory()));
        return kbRequest;
    }

    /**
     * 转换历史消息格式
     */
    private List<com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message> convertHistory(
            List<ChatRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        List<com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message> kbHistory = new ArrayList<>();
        for (ChatRequest.Message msg : history) {
            com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message kbMsg = new com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest.Message();
            kbMsg.setRole(msg.getRole());
            kbMsg.setContent(msg.getContent());
            kbHistory.add(kbMsg);
        }
        return kbHistory;
    }

    /**
     * 获取问答模型
     * 如果指定了modelId，则使用指定的模型；否则使用默认模型
     */
    private QAModel getQAModel(Long modelId) {
        if (modelId != null) {
            // 使用指定的模型
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (optional.isPresent()) {
                QAModel model = optional.get();
                // 检查模型是否启用且未删除
                if ((model.getDeleted() == null || model.getDeleted() == 0)
                        && model.getEnabled() != null && model.getEnabled()) {
                    // 检查使用场景
                    if ("chat".equals(model.getUseFor()) || "both".equals(model.getUseFor())) {
                        return model;
                    }
                }
            }
            throw new IllegalStateException("指定的模型不可用或未启用");
        } else {
            // 使用默认模型（使用场景为 chat 或 both）
            Optional<QAModel> defaultModel = qaModelRepository.findDefaultByUseFor("chat");
            if (defaultModel.isPresent()) {
                QAModel model = defaultModel.get();
                if (model.getEnabled() != null && model.getEnabled()) {
                    return model;
                }
            }

            // 如果没有默认模型，尝试获取第一个启用的模型
            List<QAModel> enabledModels = qaModelRepository.findByUseFor("chat");
            if (!enabledModels.isEmpty()) {
                return enabledModels.get(0);
            }

            // 如果数据库中没有模型，返回null
            return null;
        }
    }

}
