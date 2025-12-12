package com.github.app.dify.chat.service;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import reactor.core.publisher.Flux;
/**
 * 智能问答服务接口（直接对话，不使用知识库）
 */
public interface ChatService {
    
    /**
     * 智能问答（非流式）
     */
    ChatResponse chat(ChatRequest request, Long userId);
    
    /**
     * 智能问答（流式）
     */
    Flux<ChatResponse> chatStream(ChatRequest request, Long userId);
}