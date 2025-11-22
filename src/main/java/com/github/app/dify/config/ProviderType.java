package com.github.app.dify.config;

/**
 * AI 模型提供商类型枚举
 * 
 * 支持的提供商类型：
 * - OPENAI: OpenAI 兼容格式（包括 OpenAI、SiliconFlow、VLLM 等）
 * - OLLAMA: Ollama 本地模型服务
 * 
 * @author DifyApp
 */
public enum ProviderType {
    
    /**
     * OpenAI 兼容格式（默认）
     * 支持 OpenAI、SiliconFlow、VLLM 等兼容 OpenAI API 的服务
     * 
     * API 路径：
     * - Embedding: /v1/embeddings
     * - Chat: /v1/chat/completions
     * 
     * 需要 API Key（除非服务端未启用认证）
     */
    OPENAI("openai", "/v1/embeddings", "/v1/chat/completions", true),
    
    /**
     * Ollama 本地模型服务
     * 
     * API 路径：
     * - Embedding: /api/embeddings
     * - Chat: /api/chat
     * 
     * 不需要 API Key
     */
    OLLAMA("ollama", "/api/embeddings", "/api/chat", false);
    
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
    
    /**
     * 获取 provider 字符串值（用于配置文件）
     */
    public String getValue() {
        return value;
    }
    
    /**
     * 获取 Embedding API 路径
     */
    public String getEmbeddingPath() {
        return embeddingPath;
    }
    
    /**
     * 获取 Chat API 路径
     */
    public String getChatPath() {
        return chatPath;
    }
    
    /**
     * 是否需要 API Key
     */
    public boolean requiresApiKey() {
        return requiresApiKey;
    }
    
    /**
     * 根据字符串值获取 ProviderType
     * 
     * @param value provider 字符串值
     * @return ProviderType，如果未找到则返回 OPENAI（默认）
     */
    public static ProviderType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return OPENAI; // 默认值
        }
        
        String lowerValue = value.toLowerCase().trim();
        for (ProviderType type : ProviderType.values()) {
            if (type.value.equals(lowerValue)) {
                return type;
            }
        }
        
        // 如果未找到匹配的类型，返回默认值
        return OPENAI;
    }
    
    /**
     * 检查是否为有效的 provider 值
     */
    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true; // 空值视为有效（使用默认值）
        }
        
        String lowerValue = value.toLowerCase().trim();
        for (ProviderType type : ProviderType.values()) {
            if (type.value.equals(lowerValue)) {
                return true;
            }
        }
        return false;
    }
}

