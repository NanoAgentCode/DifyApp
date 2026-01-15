package com.github.app.dify.documentreader.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.repository.DocumentReaderRepository;
import com.github.app.dify.documentreader.req.DocumentQARequest;
import com.github.app.dify.documentreader.resp.DocumentQAResponse;
import com.github.app.dify.documentreader.service.DocumentReaderQAService;
import com.github.app.dify.documentreader.service.DocumentReaderRetrievalService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.StreamingChatLanguageModel;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.model.service.ModelConfigService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import com.github.app.dify.system.util.SkillLoader;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档解读问答服务实现
 */
@Service
public class DocumentReaderQAServiceImpl implements DocumentReaderQAService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentReaderQAServiceImpl.class);
    
    @Autowired
    private DocumentReaderRetrievalService documentReaderRetrievalService;
    
    @Autowired
    private DocumentReaderRepository documentRepository;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private DocumentReaderConfig documentReaderConfig;
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    @Autowired
    private ContextCompressionService contextCompressionService;
    
    /**
     * 文档问答（非流式）
     */
    @Override
    public DocumentQAResponse answer(Long documentId, DocumentQARequest request, Long userId) {
        try {
            DocumentReader document = validateDocumentForQA(documentId, userId);
            if (document == null) {
                return createErrorResponse("文档尚未完成向量化，无法进行问答。请等待向量化完成后再试。", 
                        request.getConversationId());
            }
            
            // 检索相关文档片段
            List<DocumentReaderRetrievalService.RetrievalResult> retrievalResults = 
                    documentReaderRetrievalService.retrieve(documentId, request.getQuestion());
            
            if (retrievalResults.isEmpty()) {
                return createErrorResponse("抱歉，在文档中没有找到相关信息。", request.getConversationId());
            }
            
            // 构建消息列表（包含历史对话和检索到的文档片段）
            List<ChatMessage> messages = buildMessages(request, retrievalResults, document.getOriginalFileName());
            
            // 应用上下文压缩策略（压缩历史对话和文档内容）
            messages = contextCompressionService.compressContext(messages, request);
            logger.debug("压缩后的消息列表大小: {}", messages.size());
            
            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            
            // 生成答案
            Response<AiMessage> aiResponse = chatLanguageModel.generate(messages);
            String answer = aiResponse.content().text();
            
            // 获取或创建会话（文档问答类型设为3）
            Long conversationId = null;
            try {
                Long requestConversationId = request.getConversationId();
                conversationId = chatHistoryService.getOrCreateConversation(
                        userId, requestConversationId, 3, null, documentId, request.getQuestion());
                logger.info("非流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                        requestConversationId, conversationId);
            } catch (Exception e) {
                logger.error("获取或创建会话失败", e);
                // 不抛出异常，继续执行
            }
            
            // 构建响应
            DocumentQAResponse response = new DocumentQAResponse();
            response.setAnswer(answer);
            response.setConversationId(conversationId != null ? String.valueOf(conversationId) : null);
            response.setFinished(true);
            
            // 构建来源文档列表
            List<DocumentQAResponse.SourceDocument> sources = retrievalResults.stream()
                    .map(result -> {
                        DocumentQAResponse.SourceDocument source = new DocumentQAResponse.SourceDocument();
                        source.setDocumentId(result.getDocumentId());
                        source.setChunkIndex(result.getChunkIndex());
                        source.setText(result.getText());
                        source.setScore(result.getScore());
                        return source;
                    })
                    .collect(Collectors.toList());
            response.setSources(sources);
            
            // 保存消息到会话历史
            if (conversationId != null) {
                try {
                    // 获取token信息
                    Long modelId = qaModel.getId();
                    Long promptTokens = aiResponse.tokenUsage() != null ? (long) aiResponse.tokenUsage().inputTokenCount() : null;
                    Long completionTokens = aiResponse.tokenUsage() != null ? (long) aiResponse.tokenUsage().outputTokenCount() : null;
                    Long totalTokens = aiResponse.tokenUsage() != null ? (long) aiResponse.tokenUsage().totalTokenCount() : null;
                    
                    // 保存用户消息
                    chatHistoryService.saveMessage(conversationId, "user", request.getQuestion());
                    // 保存助手消息（带token信息）
                    chatHistoryService.saveMessage(conversationId, "assistant", answer, 
                            modelId, promptTokens, completionTokens, totalTokens);
                } catch (Exception e) {
                    logger.error("保存历史记录失败", e);
                    // 不抛出异常，避免影响主流程
                }
            }
            
            logger.info("文档问答完成 - 文档ID: {}, 问题: {}, 答案长度: {}", documentId, request.getQuestion(), answer.length());
            return response;
            
        } catch (Exception e) {
            logger.error("文档问答失败 - 文档ID: {}", documentId, e);
            return createErrorResponse("文档问答失败：" + e.getMessage(), request.getConversationId());
        }
    }
    
    /**
     * 文档问答（流式）
     */
    @Override
    public Flux<DocumentQAResponse> answerStream(Long documentId, DocumentQARequest request, Long userId) {
        try {
            DocumentReader document = validateDocumentForQA(documentId, userId);
            if (document == null) {
                return Flux.just(createStreamErrorResponse(
                        "文档尚未完成向量化，无法进行问答。请等待向量化完成后再试。"));
            }
            
            // 检索相关文档片段
            List<DocumentReaderRetrievalService.RetrievalResult> retrievalResults = 
                    documentReaderRetrievalService.retrieve(documentId, request.getQuestion());
            
            if (retrievalResults.isEmpty()) {
                return Flux.just(createStreamErrorResponse("抱歉，在文档中没有找到相关信息。"));
            }
            
            // 构建消息列表
            List<ChatMessage> messages = buildMessages(request, retrievalResults, document.getOriginalFileName());
            
            // 应用上下文压缩策略（压缩历史对话和文档内容）
            messages = contextCompressionService.compressContext(messages, request);
            logger.debug("压缩后的消息列表大小: {}", messages.size());
            
            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());
            StreamingChatLanguageModel streamingChatLanguageModel = 
                    modelLanguageModelFactory.createStreamingChatLanguageModel(qaModel);
            
            // 获取或创建会话（文档问答类型设为3）
            final Long[] conversationIdRef = new Long[1];
            try {
                Long requestConversationId = request.getConversationId();
                conversationIdRef[0] = chatHistoryService.getOrCreateConversation(
                        userId, requestConversationId, 3, null, documentId, request.getQuestion());
                logger.info("流式响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}", 
                        requestConversationId, conversationIdRef[0]);
                
                // 保存用户消息
                if (conversationIdRef[0] != null) {
                    chatHistoryService.saveMessage(conversationIdRef[0], "user", request.getQuestion());
                }
            } catch (Exception e) {
                logger.error("获取或创建会话失败", e);
                // 不抛出异常，继续执行
            }
            
            // 使用Sink来管理流式响应
            Sinks.Many<DocumentQAResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
            
            // 构建来源文档列表
            List<DocumentQAResponse.SourceDocument> sources = retrievalResults.stream()
                    .map(result -> {
                        DocumentQAResponse.SourceDocument source = new DocumentQAResponse.SourceDocument();
                        source.setDocumentId(result.getDocumentId());
                        source.setChunkIndex(result.getChunkIndex());
                        source.setText(result.getText());
                        source.setScore(result.getScore());
                        return source;
                    })
                    .collect(Collectors.toList());
            
            // 流式生成答案
            StringBuilder answerBuilder = new StringBuilder();
            logger.info("开始流式生成答案 - 文档ID: {}, 问题: {}", documentId, request.getQuestion());
            
            streamingChatLanguageModel.generateStream(messages)
                    .doOnNext(token -> {
                        // 每次收到一个token，累积到answerBuilder中，然后发送累积的完整内容
                        // token直接是String类型，不需要调用.content()
                        if (token != null && !token.isEmpty()) {
                            answerBuilder.append(token);
                            
                            DocumentQAResponse response = new DocumentQAResponse();
                            // 发送累积的完整内容，而不是单个token
                            response.setAnswer(answerBuilder.toString());
                            response.setConversationId(conversationIdRef[0] != null ? String.valueOf(conversationIdRef[0]) : null);
                            response.setFinished(false);
                            
                            Sinks.EmitResult emitResult = sink.tryEmitNext(response);
                            if (emitResult.isFailure()) {
                                logger.warn("发送流式响应失败 - 文档ID: {}, 原因: {}", documentId, emitResult);
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 流结束，发送最终响应（包含完整答案和来源）
                        logger.info("流式生成完成 - 文档ID: {}, 答案长度: {}", documentId, answerBuilder.length());
                        String finalAnswer = answerBuilder.toString();
                        
                        // 保存助手消息到会话历史
                        if (conversationIdRef[0] != null) {
                            try {
                                Long modelId = qaModel.getId();
                                // 流式响应无法获取准确的token信息，传null
                                chatHistoryService.saveMessage(conversationIdRef[0], "assistant", finalAnswer, 
                                        modelId, null, null, null);
                            } catch (Exception e) {
                                logger.error("保存历史记录失败", e);
                                // 不抛出异常，避免影响主流程
                            }
                        }
                        
                        DocumentQAResponse finalResponse = new DocumentQAResponse();
                        finalResponse.setAnswer(finalAnswer);
                        finalResponse.setConversationId(conversationIdRef[0] != null ? String.valueOf(conversationIdRef[0]) : null);
                        finalResponse.setFinished(true);
                        finalResponse.setSources(sources);
                        Sinks.EmitResult emitResult = sink.tryEmitNext(finalResponse);
                        if (emitResult.isFailure()) {
                            logger.warn("发送最终响应失败 - 文档ID: {}, 原因: {}", documentId, emitResult);
                        }
                        sink.tryEmitComplete();
                    })
                    .doOnError(error -> {
                        logger.error("流式生成答案失败 - 文档ID: {}", documentId, error);
                        DocumentQAResponse errorResponse = new DocumentQAResponse();
                        errorResponse.setAnswer("系统繁忙，请稍后重试");
                        errorResponse.setFinished(true);
                        sink.tryEmitNext(errorResponse);
                        sink.tryEmitComplete();
                    })
                    .subscribe(
                            null, // onNext已在doOnNext中处理
                            error -> logger.error("流式订阅发生错误 - 文档ID: {}", documentId, error),
                            () -> logger.info("流式订阅完成 - 文档ID: {}", documentId)
                    );
            
            logger.info("返回流式响应Flux - 文档ID: {}", documentId);
            return sink.asFlux();
            
        } catch (Exception e) {
            logger.error("文档问答失败（流式） - 文档ID: {}", documentId, e);
            return Flux.just(createStreamErrorResponse("系统繁忙，请稍后重试"));
        }
    }
    
    /**
     * 构建消息列表
     */
    private List<ChatMessage> buildMessages(DocumentQARequest request, 
                                            List<DocumentReaderRetrievalService.RetrievalResult> retrievalResults,
                                            String documentName) {
        List<ChatMessage> messages = new ArrayList<>();
        
        String base = SkillLoader.loadSkill("document_qa_system_prompt");
        if (base == null || base.trim().isEmpty()) {
            base = "你是一个专业的文档问答助手。请基于以下文档内容回答用户的问题。\n\n";
        }
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(base).append("\n");
        systemPrompt.append("文档名称：").append(documentName != null ? documentName : "未知文档").append("\n\n");
        systemPrompt.append("相关文档片段：\n");
        for (int i = 0; i < retrievalResults.size(); i++) {
            DocumentReaderRetrievalService.RetrievalResult result = retrievalResults.get(i);
            systemPrompt.append("片段").append(i + 1).append("（相似度：").append(String.format("%.2f", result.getScore())).append("）：\n");
            systemPrompt.append(result.getText()).append("\n\n");
        }
        // 作答要求与格式已在技能文件中描述
        
        messages.add(SystemMessage.from(systemPrompt.toString()));
        
        // 添加历史对话
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            for (Map<String, String> historyItem : request.getHistory()) {
                String role = historyItem.get("role");
                String content = historyItem.get("content");
                if (role != null && content != null) {
                    if ("user".equals(role)) {
                        messages.add(UserMessage.from(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(AiMessage.from(content));
                    }
                }
            }
        }
        
        // 添加当前问题
        messages.add(UserMessage.from(request.getQuestion()));
        
        return messages;
    }
    
    /**
     * 获取问答模型
     */
    private QAModel getQAModel(Long modelId) {
        QAModel qaModel;
        if (modelId != null) {
            qaModel = modelConfigService.getQAModelById(modelId);
            if (qaModel == null) {
                throw new NotFoundException("模型不存在");
            }
        } else {
            // 使用文档解读配置中的默认问答模型
            Long defaultQAModelId = documentReaderConfig.getDefaultQAModelId();
            if (defaultQAModelId != null) {
                qaModel = modelConfigService.getQAModelById(defaultQAModelId);
                if (qaModel == null) {
                    logger.warn("文档解读配置的默认问答模型不存在，ID: {}，尝试使用系统默认RAG模型", defaultQAModelId);
                    qaModel = modelConfigService.getDefaultQAModelForRAG();
                }
            } else {
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
            
            if (qaModel == null) {
                throw new BusinessException("未配置默认问答模型，请在系统配置中设置documentReader.defaultQAModelId", ErrorCode.MODEL_NOT_FOUND);
            }
        }
        
        if (qaModel.getEnabled() == null || !qaModel.getEnabled()) {
            throw new BusinessException("模型未启用", ErrorCode.MODEL_NOT_FOUND);
        }
        
        return qaModel;
    }
    
    /**
     * 验证文档是否可以用于问答
     * @return DocumentReader 如果验证通过，null 如果文档未向量化
     * @throws BusinessException 如果文档不存在或无权访问
     */
    private DocumentReader validateDocumentForQA(Long documentId, Long userId) {
        Optional<DocumentReader> docOptional = documentRepository.findByIdAndDeleted(documentId, 0);
        if (!docOptional.isPresent()) {
            throw new NotFoundException("文档不存在");
        }
        
        DocumentReader document = docOptional.get();
        if (!document.getUserId().equals(userId)) {
            throw new BusinessException("无权访问此文档", ErrorCode.FORBIDDEN);
        }
        
        // 检查文档是否已向量化
        if (document.getVectorizedStatus() == null || document.getVectorizedStatus() != 2) {
            return null; // 返回null表示未向量化
        }
        
        return document;
    }
    
    /**
     * 创建错误响应（非流式）
     */
    private DocumentQAResponse createErrorResponse(String message, Long conversationId) {
        DocumentQAResponse response = new DocumentQAResponse();
        response.setAnswer(message);
        response.setConversationId(conversationId != null ? String.valueOf(conversationId) : null);
        response.setFinished(true);
        response.setSources(new ArrayList<>());
        return response;
    }
    
    /**
     * 创建错误响应（流式）
     */
    private DocumentQAResponse createStreamErrorResponse(String message) {
        DocumentQAResponse response = new DocumentQAResponse();
        response.setAnswer(message);
        response.setFinished(true);
        return response;
    }
}
