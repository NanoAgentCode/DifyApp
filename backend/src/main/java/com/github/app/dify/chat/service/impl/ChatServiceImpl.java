package com.github.app.dify.chat.service.impl;

import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatService;
import com.github.app.dify.chat.service.ChatStreamExecutionService;
import com.github.app.dify.chat.service.ChatSyncExecutionService;
import com.github.app.dify.ops.observability.annotation.LLMTrace;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 问答服务入口：仅保留接口与观测切面，具体执行流程分别交给同步、流式执行服务。
 */
@Service
public class ChatServiceImpl implements ChatService {

    private final ChatSyncExecutionService syncExecutionService;
    private final ChatStreamExecutionService streamExecutionService;

    public ChatServiceImpl(ChatSyncExecutionService syncExecutionService,
            ChatStreamExecutionService streamExecutionService) {
        this.syncExecutionService = syncExecutionService;
        this.streamExecutionService = streamExecutionService;
    }

    @Override
    @LLMTrace(traceSource = "Chat", conversationIdParam = "request.conversationId", extractFromReturn = true)
    public ChatResponse chat(ChatRequest request, Long userId) {
        return syncExecutionService.execute(request, userId);
    }

    @Override
    @LLMTrace(traceSource = "Chat", conversationIdParam = "request.conversationId")
    public Flux<ChatResponse> chatStream(ChatRequest request, Long userId) {
        return streamExecutionService.execute(request, userId);
    }
}
