package com.github.app.dify.service.impl;

import com.github.app.dify.config.RagConfig;
import com.github.app.dify.domain.QAModel;
import com.github.app.dify.langchain4j.CustomEmbeddingModel;
import com.github.app.dify.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.langchain4j.VectorStoreFactory;
import com.github.app.dify.req.KnowledgeBaseQARequest;
import com.github.app.dify.resp.KnowledgeBaseQAResponse;
import com.github.app.dify.service.ChatHistoryService;
import com.github.app.dify.service.ContextCompressionService;
import com.github.app.dify.service.KnowledgeBaseQAService;
import com.github.app.dify.service.KnowledgeBaseService;
import com.github.app.dify.service.ModelConfigService;
import com.github.app.dify.service.RagRetrievalService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private RagConfig ragConfig;
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private VectorStoreFactory vectorStoreFactory;
    
    @Autowired
    private ContextCompressionService contextCompressionService;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    @Autowired
    private com.github.app.dify.service.UserKnowledgeBaseVisibilityService userKnowledgeBaseVisibilityService;
    
    /**
     * 问答（非流式）
     */
    @Override
    public KnowledgeBaseQAResponse answer(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId, Integer userRole) {
        try {
            // 验证用户权限：检查用户是否有权限访问该知识库
            validateKnowledgeBaseAccess(knowledgeBaseId, userId, userRole);
            
            // 获取知识库的配置信息（向量化模型ID和topK）
            Long embeddingModelId = null;
            Integer topK = null;
            try {
                com.github.app.dify.resp.KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
                embeddingModelId = kb.getEmbeddingModelId();
                topK = kb.getTopK();
            } catch (Exception e) {
                logger.warn("获取知识库配置失败，使用默认配置 - 知识库ID: {}", knowledgeBaseId, e);
            }
            
            // 先检索相关文档（用于构建sources）
            List<RagRetrievalService.RetrievalResult> retrievalResults = 
                    ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion(), embeddingModelId, topK);
            
            if (retrievalResults.isEmpty()) {
                KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                response.setAnswer("抱歉，在知识库中没有找到相关信息。");
                response.setSources(new ArrayList<>());
                return response;
            }
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request);
            
            // 记录历史对话信息
            if (request.getHistory() != null && !request.getHistory().isEmpty()) {
                logger.info("使用历史对话，历史消息数量: {}", request.getHistory().size());
            }
            logger.debug("构建的消息列表大小: {}", messages.size());
            
            // 应用上下文压缩策略
            messages = contextCompressionService.compressContext(messages, request);
            logger.debug("压缩后的消息列表大小: {}", messages.size());
            
            // 使用langchain4j RAG生成答案（始终使用历史对话）
            String answer = generateAnswerWithHistory(messages, knowledgeBaseId, request);
            
            // 保存历史记录
            Long conversationId = null;
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
                    conversationId = chatHistoryService.getOrCreateConversation(
                            userId, requestConversationId, 2, null, knowledgeBaseId, request.getQuestion());
                    logger.info("非流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                            requestConversationId, conversationId);
                    chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                    chatHistoryService.saveMessage(conversationId, "assistant", answer);
                } catch (Exception e) {
                    logger.error("保存历史记录失败", e);
                    // 不抛出异常，避免影响主流程
                }
            }
            
            // 构建响应
            KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
            response.setAnswer(answer);
            response.setConversationId(conversationId != null ? conversationId.toString() : null);
            response.setSources(retrievalResults.stream().map(r -> {
                KnowledgeBaseQAResponse.SourceDocument source = new KnowledgeBaseQAResponse.SourceDocument();
                source.setDocumentId(r.getDocumentId());
                source.setChunkIndex(r.getChunkIndex());
                source.setText(r.getText());
                source.setScore(r.getScore());
                return source;
            }).collect(Collectors.toList()));
            
            logger.info("知识库问答完成 - 知识库ID: {}, 问题: {}", knowledgeBaseId, request.getQuestion());
            
            return response;
            
        } catch (Exception e) {
            logger.error("知识库问答失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("知识库问答失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 问答（流式）
     */
    @Override
    public Flux<KnowledgeBaseQAResponse> answerStream(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId, Integer userRole) {
        try {
            // 验证用户权限：检查用户是否有权限访问该知识库
            validateKnowledgeBaseAccess(knowledgeBaseId, userId, userRole);
            
            // 获取知识库的配置信息（向量化模型ID和topK）
            Long embeddingModelId = null;
            Integer topK = null;
            try {
                com.github.app.dify.resp.KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
                embeddingModelId = kb.getEmbeddingModelId();
                topK = kb.getTopK();
            } catch (Exception e) {
                logger.warn("获取知识库配置失败，使用默认配置 - 知识库ID: {}", knowledgeBaseId, e);
            }
            
            // 先检索相关文档（用于构建sources）
            List<RagRetrievalService.RetrievalResult> retrievalResults = 
                    ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion(), embeddingModelId, topK);
            
            if (retrievalResults.isEmpty()) {
                KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                response.setAnswer("抱歉，在知识库中没有找到相关信息。");
                response.setFinished(true);
                response.setSources(new ArrayList<>());
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
            
            // 构建消息列表（包含历史对话）
            List<ChatMessage> messages = buildMessages(request);
            
            // 应用上下文压缩策略
            messages = contextCompressionService.compressContext(messages, request);
            logger.debug("压缩后的消息列表大小（流式）: {}", messages.size());
            
            // 使用langchain4j流式LLM生成答案（手动检索+RAG）
            return generateStreamAnswerWithHistory(messages, knowledgeBaseId, request, sources, userId);
            
        } catch (Exception e) {
            logger.error("知识库问答失败（流式） - 知识库ID: {}", knowledgeBaseId, e);
            return Flux.error(new RuntimeException("知识库问答失败: " + e.getMessage(), e));
        }
    }
    
    /**
     * 创建ContentRetriever
     */
    private ContentRetriever createContentRetriever(Long knowledgeBaseId) {
        EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createEmbeddingStore(knowledgeBaseId);
        
        // 获取知识库的topK配置，如果为null则使用全局配置
        Integer topK = null;
        try {
            com.github.app.dify.resp.KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
            topK = kb.getTopK();
        } catch (Exception e) {
            logger.debug("获取知识库topK配置失败，使用全局配置 - 知识库ID: {}", knowledgeBaseId);
        }
        int effectiveTopK = (topK != null && topK > 0) ? topK : ragConfig.getTopK();
        
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(effectiveTopK)
                .minScore(ragConfig.getSimilarityThreshold())
                .build();
    }
    
    /**
     * 构建消息列表（包含历史对话）
     */
    private List<ChatMessage> buildMessages(KnowledgeBaseQARequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 添加系统消息
        messages.add(SystemMessage.from("你是一个专业的AI助手，基于提供的知识库内容回答问题。" +
                "如果知识库中没有相关信息，请明确说明无法回答。" +
                "\n\n重要：请使用Markdown格式来组织你的回答，包括：\n" +
                "- 使用标题（#、##、###）来组织内容结构\n" +
                "- 使用列表（-、*、1.）来列举要点\n" +
                "- 使用代码块（```）来展示代码或技术内容\n" +
                "- 使用**粗体**和*斜体*来强调重要信息\n" +
                "- 使用表格来展示结构化数据\n" +
                "- 确保代码块包含正确的语言标识符\n" +
                "\n【关键要求】数学公式格式（必须严格遵守）：\n" +
                "1. 所有数学公式必须使用LaTeX格式编写，不要使用占位符或省略公式内容\n" +
                "2. 行内公式使用 $...$ 格式，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$\n" +
                "3. 块级公式使用 $$...$$ 格式，例如：\n" +
                "   $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$\n" +
                "4. 也可以使用 [...] 格式表示块级公式，例如：\n" +
                "   [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]\n" +
                "5. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整的LaTeX公式\n" +
                "6. 公式中的特殊字符需要使用反斜杠转义，例如：\\frac{分子}{分母}、\\sqrt{内容}、\\sum_{i=1}^{n} 等\n" +
                "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符"));
        
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
    
    /**
     * 使用历史对话生成答案（非流式）
     */
    private String generateAnswerWithHistory(List<ChatMessage> messages, Long knowledgeBaseId, KnowledgeBaseQARequest request) {
        // 获取最后一个用户消息作为查询
        String query = request.getQuestion();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (lastMessage instanceof UserMessage) {
            query = ((UserMessage) lastMessage).singleText();
        }
        
        // 获取知识库的topK配置
        Integer topK = null;
        try {
            com.github.app.dify.resp.KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
            topK = kb.getTopK();
        } catch (Exception e) {
            logger.debug("获取知识库topK配置失败，使用全局配置 - 知识库ID: {}", knowledgeBaseId);
        }
        
        // 使用RagRetrievalService检索相关内容（它已经处理了ContentRetriever的调用）
        List<RagRetrievalService.RetrievalResult> retrievalResults = 
                ragRetrievalService.retrieve(knowledgeBaseId, query, null, topK);
        
        // 构建包含检索内容的系统消息
        if (!retrievalResults.isEmpty()) {
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("基于以下知识库内容回答问题：\n\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                contextBuilder.append("文档片段 ").append(i + 1).append(":\n");
                contextBuilder.append(retrievalResults.get(i).getText()).append("\n\n");
            }
            
            // 替换系统消息
            messages.set(0, SystemMessage.from(contextBuilder.toString() +
                    "如果知识库中没有相关信息，请明确说明无法回答。" +
                    "\n\n重要：请使用Markdown格式来组织你的回答，包括：\n" +
                    "- 使用标题（#、##、###）来组织内容结构\n" +
                    "- 使用列表（-、*、1.）来列举要点\n" +
                    "- 使用代码块（```）来展示代码或技术内容\n" +
                    "- 使用**粗体**和*斜体*来强调重要信息\n" +
                    "- 使用表格来展示结构化数据\n" +
                    "- 确保代码块包含正确的语言标识符\n" +
                    "\n【关键要求】数学公式格式（必须严格遵守）：\n" +
                    "1. 所有数学公式必须使用LaTeX格式编写，不要使用占位符或省略公式内容\n" +
                    "2. 行内公式使用 $...$ 格式，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$\n" +
                    "3. 块级公式使用 $$...$$ 格式，例如：\n" +
                    "   $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$\n" +
                    "4. 也可以使用 [...] 格式表示块级公式，例如：\n" +
                    "   [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]\n" +
                    "5. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整的LaTeX公式\n" +
                    "6. 公式中的特殊字符需要使用反斜杠转义，例如：\\frac{分子}{分母}、\\sqrt{内容}、\\sum_{i=1}^{n} 等\n" +
                    "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符"));
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
        
        // 创建模型实例
        ModelLanguageModelFactory.ChatLanguageModel chatLanguageModel = 
                modelLanguageModelFactory.createChatLanguageModel(qaModel);
        
        // 调用LLM生成答案
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }
    
    /**
     * 使用历史对话生成流式答案
     */
    private Flux<KnowledgeBaseQAResponse> generateStreamAnswerWithHistory(
            List<ChatMessage> messages, Long knowledgeBaseId, 
            KnowledgeBaseQARequest request, List<KnowledgeBaseQAResponse.SourceDocument> sources, Long userId) {
        // 获取最后一个用户消息作为查询
        String query = request.getQuestion();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (lastMessage instanceof UserMessage) {
            query = ((UserMessage) lastMessage).singleText();
        }
        
        // 使用RagRetrievalService检索相关内容
        List<RagRetrievalService.RetrievalResult> retrievalResults = 
                ragRetrievalService.retrieve(knowledgeBaseId, query);
        
        // 构建包含检索内容的系统消息
        if (!retrievalResults.isEmpty()) {
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("基于以下知识库内容回答问题：\n\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                contextBuilder.append("文档片段 ").append(i + 1).append(":\n");
                contextBuilder.append(retrievalResults.get(i).getText()).append("\n\n");
            }
            
            // 替换系统消息
            messages.set(0, SystemMessage.from(contextBuilder.toString() +
                    "如果知识库中没有相关信息，请明确说明无法回答。" +
                    "\n\n重要：请使用Markdown格式来组织你的回答，包括：\n" +
                    "- 使用标题（#、##、###）来组织内容结构\n" +
                    "- 使用列表（-、*、1.）来列举要点\n" +
                    "- 使用代码块（```）来展示代码或技术内容\n" +
                    "- 使用**粗体**和*斜体*来强调重要信息\n" +
                    "- 使用表格来展示结构化数据\n" +
                    "- 确保代码块包含正确的语言标识符\n" +
                    "\n【关键要求】数学公式格式（必须严格遵守）：\n" +
                    "1. 所有数学公式必须使用LaTeX格式编写，不要使用占位符或省略公式内容\n" +
                    "2. 行内公式使用 $...$ 格式，例如：$E = mc^2$ 或 $\\phi = \\frac{1+\\sqrt{5}}{2}$\n" +
                    "3. 块级公式使用 $$...$$ 格式，例如：\n" +
                    "   $$F(n) = \\frac{1}{\\sqrt{5}} \\left( \\left( \\frac{1 + \\sqrt{5}}{2} \\right)^n - \\left( \\frac{1 - \\sqrt{5}}{2} \\right)^n \\right)$$\n" +
                    "4. 也可以使用 [...] 格式表示块级公式，例如：\n" +
                    "   [ f(x) = \\sum_{n=0}^{\\infty} \\frac{f^{(n)}(a)}{n!}(x-a)^n ]\n" +
                    "5. 绝对禁止使用占位符（如 <!--KATEX_FORMULA_X--> 或类似格式），必须写出完整的LaTeX公式\n" +
                    "6. 公式中的特殊字符需要使用反斜杠转义，例如：\\frac{分子}{分母}、\\sqrt{内容}、\\sum_{i=1}^{n} 等\n" +
                    "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符"));
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
        
        // 创建流式模型实例
        ModelLanguageModelFactory.StreamingChatLanguageModel streamingModel = 
                modelLanguageModelFactory.createStreamingChatLanguageModel(qaModel);
        
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
        
        // 在流式响应开始前，先创建或获取会话（这样可以在第一个数据包就返回 conversationId）
        final java.util.concurrent.atomic.AtomicReference<Long> conversationIdRef = 
                new java.util.concurrent.atomic.AtomicReference<>(null);
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
                        userId, requestConversationId, 2, null, knowledgeBaseId, request.getQuestion());
                conversationIdRef.set(conversationId);
                logger.info("流式响应开始 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                        requestConversationId, conversationId);
                // 先保存用户消息
                chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
            } catch (Exception e) {
                logger.error("创建会话失败（流式）", e);
                // 不抛出异常，避免影响主流程
            }
        }
        
        // 使用scan累积答案，并保存最后一个完整答案
        final java.util.concurrent.atomic.AtomicReference<String> lastAnswer = new java.util.concurrent.atomic.AtomicReference<>("");
        
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
                    response.setConversationId(conversationIdRef.get() != null ? conversationIdRef.get().toString() : null);
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
                    if (userId != null && conversationId != null && finalAnswer != null && !finalAnswer.trim().isEmpty()) {
                        try {
                            chatHistoryService.saveMessage(conversationId, "assistant", finalAnswer);
                            logger.info("流式响应完成 - 保存助手消息到会话: {}", conversationId);
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
                    errorResponse.setAnswer("生成答案时发生错误: " + error.getMessage());
                    errorResponse.setSources(sources);
                    errorResponse.setFinished(true);
                    return Flux.just(errorResponse);
                });
    }
    
    /**
     * 验证用户是否有权限访问知识库
     * @param knowledgeBaseId 知识库ID
     * @param userId 用户ID
     * @param userRole 用户角色（1-管理员，0-普通用户），如果为null则按普通用户处理
     * @throws RuntimeException 如果用户没有权限访问该知识库
     */
    private void validateKnowledgeBaseAccess(Long knowledgeBaseId, Long userId, Integer userRole) {
        if (userId == null) {
            throw new RuntimeException("用户未登录，无法访问知识库");
        }
        
        // 获取知识库信息
        com.github.app.dify.resp.KnowledgeBaseResp kb;
        try {
            kb = knowledgeBaseService.getKnowledgeBaseById(knowledgeBaseId);
        } catch (Exception e) {
            logger.error("获取知识库信息失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("知识库不存在或已删除");
        }
        
        // 检查知识库是否启用
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new RuntimeException("知识库已被禁用，无法进行问答");
        }
        
        // 权限检查：管理员可以看到所有知识库（但仍需检查用户可见性设置）
        if (userRole != null && userRole == 1) {
            // 管理员还需要检查用户可见性设置（如果设置了的话）
            if (!userKnowledgeBaseVisibilityService.hasAccess(userId, knowledgeBaseId)) {
                throw new RuntimeException("您没有权限访问该知识库");
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
                throw new RuntimeException("您没有权限访问该知识库");
            }
            logger.debug("普通用户权限验证通过 - 用户ID: {}, 知识库ID: {}, 是否公开: {}, 是否创建者: {}", 
                    userId, knowledgeBaseId, isPublic, isOwner);
        } else {
            // 私有知识库且不是创建者，直接拒绝
            throw new RuntimeException("您没有权限访问该私有知识库");
        }
    }
}

