package com.github.app.dify.appsystemdata.config;

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
    
    /**
     * 上下文压缩配置
     */
    private boolean enableContextCompression = true; // 是否启用上下文压缩
    private String compressionStrategy = "sliding_window"; // 压缩策略：sliding_window, summary, hybrid
    private int maxHistoryRounds = 10; // 最大历史对话轮数（滑动窗口策略）
    private int maxHistoryTokens = 2000; // 最大历史对话token数（用于判断是否需要压缩）
    private boolean enableSummary = false; // 是否启用总结压缩（需要额外调用LLM）
    
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
    
    public boolean isEnableContextCompression() {
        return enableContextCompression;
    }
    
    public void setEnableContextCompression(boolean enableContextCompression) {
        this.enableContextCompression = enableContextCompression;
    }
    
    public String getCompressionStrategy() {
        return compressionStrategy;
    }
    
    public void setCompressionStrategy(String compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }
    
    public int getMaxHistoryRounds() {
        return maxHistoryRounds;
    }
    
    public void setMaxHistoryRounds(int maxHistoryRounds) {
        this.maxHistoryRounds = maxHistoryRounds;
    }
    
    public int getMaxHistoryTokens() {
        return maxHistoryTokens;
    }
    
    public void setMaxHistoryTokens(int maxHistoryTokens) {
        this.maxHistoryTokens = maxHistoryTokens;
    }
    
    public boolean isEnableSummary() {
        return enableSummary;
    }
    
    public void setEnableSummary(boolean enableSummary) {
        this.enableSummary = enableSummary;
    }
}

