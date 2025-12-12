package com.github.app.dify.appsystemdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
/**
 * 向量化配置类
 */
@Configuration
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingConfig {
    
    private String apiUrl = "http://localhost:8000/v1/embeddings";
    private String apiKey;
    private String model = "text-embedding-ada-002";
    private int timeout = 30000;
    private int batchSize = 100;
    /**
     * 提供商类型：openai（默认，兼容 OpenAI API，包括 SiliconFlow、VLLM 等）、ollama
     * 默认值为 openai，保持向后兼容
     */
    private String provider = "openai";
    
    public String getApiUrl() {
        return apiUrl;
    }
    
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public int getTimeout() {
        return timeout;
    }
    
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
}

