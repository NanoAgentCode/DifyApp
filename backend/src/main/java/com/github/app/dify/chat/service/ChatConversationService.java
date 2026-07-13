package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.common.util.ConversationIdUtil;
import com.github.app.dify.memory.service.UserMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会话创建、历史消息持久化及响应对象组装。
 */
@Service
public class ChatConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ChatConversationService.class);

    private final ChatHistoryService chatHistoryService;
    private final UserMemoryService userMemoryService;

    public ChatConversationService(ChatHistoryService chatHistoryService, UserMemoryService userMemoryService) {
        this.chatHistoryService = chatHistoryService;
        this.userMemoryService = userMemoryService;
    }

    public ChatResponse buildResponse(String answer, ChatRequest request, Long userId, Long conversationId,
            Long modelId, Long promptTokens, Long completionTokens, Long totalTokens) {
        if (userId != null && conversationId == null) {
            try {
                conversationId = createConversationAndSaveUserMessage(request, userId, false);
                chatHistoryService.saveMessage(conversationId, "assistant", answer,
                        modelId, promptTokens, completionTokens, totalTokens);
                updateMemory(userId, getHistoryQuestion(request), answer, modelId, conversationId);
            } catch (Exception e) {
                logger.error("保存历史记录失败", e);
            }
        }

        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setConversationId(conversationId);
        return response;
    }

    public Long createConversationAndSaveUserMessage(ChatRequest request, Long userId, boolean stream) {
        Long requestConversationId = ConversationIdUtil.parseConversationId(request.getConversationId(), logger);
        String historyQuestion = getHistoryQuestion(request);
        Long conversationId = chatHistoryService.getOrCreateConversation(
                userId, requestConversationId, getConversationType(request), null, null,
                getConversationTitle(request, historyQuestion));
        logger.info("{}响应 - 获取或创建会话，requestConversationId: {}, 返回conversationId: {}",
                stream ? "流式" : "非流式", requestConversationId, conversationId);
        chatHistoryService.saveMessage(conversationId, "user", historyQuestion);
        return conversationId;
    }

    public void saveAssistantMessage(Long conversationId, String answer, Long modelId,
            Long promptTokens, Long completionTokens, Long totalTokens) {
        chatHistoryService.saveMessage(conversationId, "assistant", answer,
                modelId, promptTokens, completionTokens, totalTokens);
    }

    public void updateMemory(Long userId, String question, String answer, Long modelId, Long conversationId) {
        try {
            userMemoryService.updateMemoryAsync(userId, question, answer, modelId, conversationId,
                    "chat", null);
        } catch (Exception e) {
            logger.debug("触发异步记忆更新失败", e);
        }
    }

    public String getHistoryQuestion(ChatRequest request) {
        if (request.getHistoryQuestion() != null && !request.getHistoryQuestion().trim().isEmpty()) {
            return request.getHistoryQuestion().trim();
        }
        return request.getQuestion();
    }

    private Integer getConversationType(ChatRequest request) {
        return request.getConversationType() != null ? request.getConversationType() : 1;
    }

    private String getConversationTitle(ChatRequest request, String fallbackQuestion) {
        if (request.getConversationTitle() != null && !request.getConversationTitle().trim().isEmpty()) {
            return request.getConversationTitle().trim();
        }
        return fallbackQuestion;
    }
}
