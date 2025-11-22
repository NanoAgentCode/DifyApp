package com.github.app.dify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG配置类
 */
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagConfig {
    
    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int topK = 5;
    private double similarityThreshold = 0.7;
    private String llmApiUrl;
    private String llmApiKey;
    private String llmModel = "gpt-3.5-turbo"; // 默认模型
    /**
     * 提供商类型：openai（默认，兼容 OpenAI API，包括 SiliconFlow、VLLM 等）、ollama
     * 默认值为 openai，保持向后兼容
     */
    private String provider = "openai";
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }
    
    public int getChunkOverlap() {
        return chunkOverlap;
    }
    
    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
    
    public int getTopK() {
        return topK;
    }
    
    public void setTopK(int topK) {
        this.topK = topK;
    }
    
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }
    
    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
    
    public String getLlmApiUrl() {
        return llmApiUrl;
    }
    
    public void setLlmApiUrl(String llmApiUrl) {
        this.llmApiUrl = llmApiUrl;
    }
    
    public String getLlmApiKey() {
        return llmApiKey;
    }
    
    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }
    
    public String getLlmModel() {
        return llmModel;
    }
    
    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
}

