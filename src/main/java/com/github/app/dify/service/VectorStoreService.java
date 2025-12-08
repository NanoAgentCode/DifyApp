package com.github.app.dify.service;

import java.util.List;

/**
 * 向量存储服务接口
 * 作为策略模式的上下文，根据知识库配置选择合适的向量存储策略
 */
public interface VectorStoreService {
    
    /**
     * 确保集合/索引存在
     */
    void ensureCollection(Long knowledgeBaseId, int vectorSize);
    
    /**
     * 批量插入/更新向量
     */
    void upsertVectors(Long knowledgeBaseId, Long documentId, 
                       List<List<Float>> vectors, List<String> texts, 
                       List<Integer> chunkIndices);
    
    /**
     * 搜索向量
     */
    List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK);
    
    /**
     * 删除文档的所有向量
     */
    void deleteDocumentVectors(Long knowledgeBaseId, Long documentId);
    
    /**
     * 获取集合名称（用于知识库）
     */
    String getCollectionName(Long knowledgeBaseId);
}
