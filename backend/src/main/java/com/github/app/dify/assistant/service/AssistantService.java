package com.github.app.dify.assistant.service;

import com.github.app.dify.assistant.req.AssistantChatReq;
import com.github.app.dify.chat.resp.ChatResponse;
import reactor.core.publisher.Flux;

public interface AssistantService {

    ChatResponse chat(AssistantChatReq request, Long userId);

    Flux<ChatResponse> chatStream(AssistantChatReq request, Long userId);
}
