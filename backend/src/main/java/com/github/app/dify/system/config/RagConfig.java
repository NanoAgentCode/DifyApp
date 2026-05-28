package com.github.app.dify.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索与上下文压缩配置（绑定 rag.*）
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    private int chunkSize = 500;
    private int chunkOverlap = 50;
    private int topK = 10;
    private double similarityThreshold = 0.3;
    private boolean enableContextCompression = true;
    private String compressionStrategy = "sliding_window";
    private int maxHistoryRounds = 10;
    private int maxHistoryTokens = 2000;
    private boolean enableSummary = false;
    private boolean enableConversationSummary = true;
    private int conversationSummaryThresholdRounds = 10;
    private int conversationSummaryUpdateIntervalRounds = 5;
    private int conversationSummaryMaxMessages = 40;
    private int maxSystemMessageLength = 32000;

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

    public boolean isEnableConversationSummary() {
        return enableConversationSummary;
    }

    public void setEnableConversationSummary(boolean enableConversationSummary) {
        this.enableConversationSummary = enableConversationSummary;
    }

    public int getConversationSummaryThresholdRounds() {
        return conversationSummaryThresholdRounds;
    }

    public void setConversationSummaryThresholdRounds(int conversationSummaryThresholdRounds) {
        this.conversationSummaryThresholdRounds = conversationSummaryThresholdRounds;
    }

    public int getConversationSummaryUpdateIntervalRounds() {
        return conversationSummaryUpdateIntervalRounds;
    }

    public void setConversationSummaryUpdateIntervalRounds(int conversationSummaryUpdateIntervalRounds) {
        this.conversationSummaryUpdateIntervalRounds = conversationSummaryUpdateIntervalRounds;
    }

    public int getConversationSummaryMaxMessages() {
        return conversationSummaryMaxMessages;
    }

    public void setConversationSummaryMaxMessages(int conversationSummaryMaxMessages) {
        this.conversationSummaryMaxMessages = conversationSummaryMaxMessages;
    }

    public int getMaxSystemMessageLength() {
        return maxSystemMessageLength;
    }

    public void setMaxSystemMessageLength(int maxSystemMessageLength) {
        this.maxSystemMessageLength = maxSystemMessageLength;
    }
}
