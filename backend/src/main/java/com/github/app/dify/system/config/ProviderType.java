package com.github.app.dify.system.config;

/**
 * LLM/Embedding 提供商类型（用于构建 API 路径与鉴权）
 */
public enum ProviderType {

    OPENAI("openai", "/v1/embeddings", "/v1/chat/completions", true),
    AZURE("azure", "/openai/deployments/embeddings/embeddings", "/openai/deployments/chat/completions", true),
    OLLAMA("ollama", "/api/embeddings", "/api/chat", false),
    DASHSCOPE("dashscope", "/api/v1/services/embeddings/text-embedding/embedding", "/api/v1/services/aigc/text-generation/generation", true),
    ZHIPU("zhipu", "/v4/embeddings", "/v4/chat/completions", true),
    OTHER("other", "/v1/embeddings", "/v1/chat/completions", true);

    private final String value;
    private final String embeddingPath;
    private final String chatPath;
    private final boolean requiresApiKey;

    ProviderType(String value, String embeddingPath, String chatPath, boolean requiresApiKey) {
        this.value = value;
        this.embeddingPath = embeddingPath;
        this.chatPath = chatPath;
        this.requiresApiKey = requiresApiKey;
    }

    public String getValue() {
        return value;
    }

    public String getEmbeddingPath() {
        return embeddingPath;
    }

    /** 聊天/文本生成 API 路径（如 /v1/chat/completions） */
    public String getChatPath() {
        return chatPath;
    }

    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    public static ProviderType fromValue(String v) {
        if (v == null || v.trim().isEmpty()) {
            return OPENAI;
        }
        String lower = v.trim().toLowerCase();
        for (ProviderType t : values()) {
            if (t.value.equals(lower)) {
                return t;
            }
        }
        return OTHER;
    }
}
