package com.github.app.dify.chat.service;

public interface ConversationSummaryService {

    void updateSummaryIfNeededAsync(Long conversationId, Long modelId);
}
