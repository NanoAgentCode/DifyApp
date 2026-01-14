package com.github.app.dify.memory.service;

public interface UserMemoryService {

    default String buildMemoryContext(Long userId, String question) {
        return buildMemoryContext(userId, question, null, null);
    }

    String buildMemoryContext(Long userId, String question, String scopeType, Long scopeId);

    default void updateMemoryAsync(Long userId, String question, String answer, Long modelId, Long conversationId) {
        updateMemoryAsync(userId, question, answer, modelId, conversationId, null, null);
    }

    void updateMemoryAsync(Long userId, String question, String answer, Long modelId, Long conversationId, String scopeType, Long scopeId);

    default void clearUserMemory(Long userId) {
        clearUserMemory(userId, null, null);
    }

    void clearUserMemory(Long userId, String scopeType, Long scopeId);

    default java.util.List<com.github.app.dify.memory.resp.UserMemoryItemResp> listUserMemory(Long userId, String memoryType, int page, int size) {
        return listUserMemory(userId, memoryType, page, size, null, null);
    }

    java.util.List<com.github.app.dify.memory.resp.UserMemoryItemResp> listUserMemory(Long userId, String memoryType, int page, int size, String scopeType, Long scopeId);
}
