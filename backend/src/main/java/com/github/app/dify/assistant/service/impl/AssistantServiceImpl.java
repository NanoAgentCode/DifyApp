package com.github.app.dify.assistant.service.impl;

import com.github.app.dify.assistant.req.AssistantChatReq;
import com.github.app.dify.assistant.service.AssistantService;
import com.github.app.dify.assistant.util.AssistantContextSanitizer;
import com.github.app.dify.assistant.util.AssistantPromptBuilder;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AssistantServiceImpl implements AssistantService {

    private final ChatService chatService;
    private final AssistantContextSanitizer sanitizer;
    private final AssistantPromptBuilder promptBuilder;

    public AssistantServiceImpl(ChatService chatService,
                                AssistantContextSanitizer sanitizer,
                                AssistantPromptBuilder promptBuilder) {
        this.chatService = chatService;
        this.sanitizer = sanitizer;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public ChatResponse chat(AssistantChatReq request, Long userId) {
        return chatService.chat(toChatRequest(request, false), userId);
    }

    @Override
    public Flux<ChatResponse> chatStream(AssistantChatReq request, Long userId) {
        return chatService.chatStream(toChatRequest(request, true), userId);
    }

    private ChatRequest toChatRequest(AssistantChatReq request, boolean stream) {
        String message = sanitizer.sanitizeMessage(request.getMessage());
        AssistantChatReq.AssistantPageContext context = sanitizer.sanitizePageContext(request.getPageContext());

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setQuestion(promptBuilder.build(message, context));
        chatRequest.setConversationId(request.getConversationId());
        chatRequest.setModelId(request.getModelId());
        chatRequest.setStream(stream);
        chatRequest.setEnableBrowserSearch(false);
        chatRequest.setEnableTimeInfo(true);
        chatRequest.setEnableMemo(false);
        chatRequest.setConversationType(5);
        chatRequest.setHistoryQuestion(message);
        chatRequest.setConversationTitle(buildConversationTitle(context, message));
        chatRequest.setHistory(convertHistory(sanitizer.sanitizeHistory(request.getHistory())));
        return chatRequest;
    }

    private String buildConversationTitle(AssistantChatReq.AssistantPageContext context, String message) {
        String pageTitle = null;
        if (context != null && context.getPage() != null) {
            pageTitle = context.getPage().getTitle();
        }
        if (pageTitle != null && !pageTitle.isBlank()) {
            return "页面助手 - " + pageTitle;
        }
        if (message != null && !message.isBlank()) {
            return "页面助手 - " + message;
        }
        return "页面助手";
    }

    private List<ChatRequest.Message> convertHistory(List<AssistantChatReq.AssistantMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream().map(item -> {
            ChatRequest.Message message = new ChatRequest.Message();
            message.setRole(item.getRole());
            message.setContent(item.getContent());
            return message;
        }).toList();
    }
}
