package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.knowledgebase.service.ContextCompressionService;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 将聊天请求转换为上下文压缩服务所需的数据。 */
@Service
public class ChatMessagePreparationService {

    private final ContextCompressionService contextCompressionService;

    public ChatMessagePreparationService(ContextCompressionService contextCompressionService) {
        this.contextCompressionService = contextCompressionService;
    }

    public List<ChatMessage> compress(List<ChatMessage> messages, ChatRequest request) {
        return contextCompressionService.compressContext(messages, toKnowledgeBaseRequest(request));
    }

    private KnowledgeBaseQARequest toKnowledgeBaseRequest(ChatRequest request) {
        KnowledgeBaseQARequest result = new KnowledgeBaseQARequest();
        result.setQuestion(request.getQuestion());
        if (request.getHistory() == null || request.getHistory().isEmpty()) {
            return result;
        }
        List<KnowledgeBaseQARequest.Message> history = new ArrayList<>();
        for (ChatRequest.Message message : request.getHistory()) {
            KnowledgeBaseQARequest.Message item = new KnowledgeBaseQARequest.Message();
            item.setRole(message.getRole());
            item.setContent(message.getContent());
            history.add(item);
        }
        result.setHistory(history);
        return result;
    }
}
