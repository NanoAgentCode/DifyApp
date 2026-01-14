package com.github.app.dify.memory.service;

public interface UserMemoryService {

    String buildMemoryContext(Long userId, String question);

    void updateMemoryAsync(Long userId, String question, String answer, Long modelId, Long conversationId);

    void clearUserMemory(Long userId);

    java.util.List<com.github.app.dify.memory.resp.UserMemoryItemResp> listUserMemory(Long userId, String memoryType, int page, int size);
}
