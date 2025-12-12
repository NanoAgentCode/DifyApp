package com.github.app.dify.appknowledgebase.service;

import java.util.List;
/**
 * 向量化服务接口
 */
public interface EmbeddingService {
    
    /**
     * 向量化单个文本（使用默认模型）
     */
    List<Float> embed(String text);
    
    /**
     * 向量化单个文本（使用指定模型）
     */
    List<Float> embed(String text, Long modelId);
    
    /**
     * 批量向量化文本（使用默认模型）
     */
    List<List<Float>> embedBatch(List<String> texts);
    
    /**
     * 批量向量化文本（使用指定模型）
     */
    List<List<Float>> embedBatch(List<String> texts, Long modelId);
    
    /**
     * 批量向量化文本（带分块处理，使用默认模型）
     */
    List<List<Float>> embedBatchWithChunking(List<String> texts);
    
    /**
     * 批量向量化文本（带分块处理，使用指定模型）
     */
    List<List<Float>> embedBatchWithChunking(List<String> texts, Long modelId);
}