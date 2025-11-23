package com.github.app.dify.service;

import com.github.app.dify.config.RagConfig;
import com.github.app.dify.langchain4j.CustomChatLanguageModel;
import com.github.app.dify.langchain4j.CustomEmbeddingModel;
import com.github.app.dify.langchain4j.CustomStreamingChatLanguageModel;
import com.github.app.dify.langchain4j.QdrantEmbeddingStore;
import com.github.app.dify.req.KnowledgeBaseQARequest;
import com.github.app.dify.resp.KnowledgeBaseQAResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 知识库问答服务（使用LangChain4j RAG）
 */
@Service
public class KnowledgeBaseQAService {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseQAService.class);
    
    @Autowired
    private RagRetrievalService ragRetrievalService;
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private CustomEmbeddingModel embeddingModel;
    
    @Autowired
    private CustomChatLanguageModel chatLanguageModel;
    
    @Autowired
    private CustomStreamingChatLanguageModel streamingChatLanguageModel;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private ContextCompressionService contextCompressionService;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    // 缓存每个知识库的RAG服务实例
    private final Map<Long, RagService> ragServiceCache = new ConcurrentHashMap<>();
    
    /**
     * RAG服务接口（非流式）
     */
    interface RagService {
        @dev.langchain4j.service.UserMessage("{{question}}")
        String answer(@dev.langchain4j.service.UserMessage String question);
    }
    
    /**
     * 问答（非流式）
     */
    public KnowledgeBaseQAResponse answer(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId) {
        try {
            // 检查LLM配置
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                throw new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url");
            }
            
            // 先检索相关文档（用于构建sources）
            List<RagRetrievalService.RetrievalResult> retrievalResults = 
                    ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion());
            
            if (retrievalResults.isEmpty()) {
                KnowledgeBaseQAResponse response = new KnowledgeBaseQAResponse();
                response.setAnswer("抱歉，在知识库中没有找到相关信息。");
                response.setSources(new ArrayList<>());
                return response;
            }
            
            // 获取或创建RAG服务实例
            RagService ragService = getOrCreateRagService(knowledgeBaseId);
            
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
    public Flux<KnowledgeBaseQAResponse> answerStream(Long knowledgeBaseId, KnowledgeBaseQARequest request, Long userId) {
        try {
            // 检查LLM配置
            if (ragConfig.getLlmApiUrl() == null || ragConfig.getLlmApiUrl().trim().isEmpty()) {
                return Flux.error(new IllegalStateException("LLM API URL未配置，请在application.yml中配置rag.llm-api-url"));
            }
            
            // 先检索相关文档（用于构建sources）
            List<RagRetrievalService.RetrievalResult> retrievalResults = 
                    ragRetrievalService.retrieve(knowledgeBaseId, request.getQuestion());
            
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
     * 获取或创建RAG服务实例
     */
    private RagService getOrCreateRagService(Long knowledgeBaseId) {
        return ragServiceCache.computeIfAbsent(knowledgeBaseId, kbId -> {
            // 创建知识库专用的ContentRetriever
            ContentRetriever contentRetriever = createContentRetriever(kbId);
            
            // 创建RAG服务
            return AiServices.builder(RagService.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(contentRetriever)
                    .systemMessageProvider(chatMemoryId -> 
                            "你是一个专业的AI助手，基于提供的知识库内容回答问题。" +
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
                            "7. 如果回答涉及数学、物理、工程等领域的公式，必须使用上述格式完整写出，不要省略或使用占位符")
                    .build();
        });
    }
    
    /**
     * 创建ContentRetriever
     */
    private ContentRetriever createContentRetriever(Long knowledgeBaseId) {
        EmbeddingStore<TextSegment> embeddingStore = QdrantEmbeddingStore.forKnowledgeBase(
                knowledgeBaseId, vectorStoreService);
        
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(ragConfig.getTopK())
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
        
        // 使用RagRetrievalService检索相关内容（它已经处理了ContentRetriever的调用）
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
        
        // 调用流式LLM生成答案
        logger.info("开始调用流式LLM生成答案 - 知识库ID: {}, 消息数量: {}", knowledgeBaseId, messages.size());
        Flux<String> tokenFlux = streamingChatLanguageModel.generateStream(messages)
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
}

