package com.github.app.dify.service;

import java.util.List;
/**
 * 向量存储策略接口
 * 定义不同向量数据库的统一操作接口
 */
public interface VectorStoreStrategy {
    
    /**
     * 获取策略类型（如：qdrant, weaviate, chroma, milvus, faiss, elasticsearch）
     */
    String getType();
    
    /**
     * 确保集合/索引存在
     * @param knowledgeBaseId 知识库ID
     * @param vectorSize 向量维度
     */
    void ensureCollection(Long knowledgeBaseId, int vectorSize);
    
    /**
     * 批量插入/更新向量
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     * @param vectors 向量列表
     * @param texts 文本列表
     * @param chunkIndices 分块索引列表
     */
    void upsertVectors(Long knowledgeBaseId, Long documentId, 
                       List<List<Float>> vectors, List<String> texts, 
                       List<Integer> chunkIndices);
    
    /**
     * 搜索向量
     * @param knowledgeBaseId 知识库ID
     * @param queryVector 查询向量
     * @param topK 返回前K个结果
     * @return 搜索结果列表
     */
    List<SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK);
    
    /**
     * 删除文档的所有向量
     * @param knowledgeBaseId 知识库ID
     * @param documentId 文档ID
     */
    void deleteDocumentVectors(Long knowledgeBaseId, Long documentId);
    
    /**
     * 搜索结果
     */
    class SearchResult {
        private double score;
        private String text;
        private Long documentId;
        private Integer chunkIndex;
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public Long getDocumentId() {
            return documentId;
        }
        
        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
        
        public Integer getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }
}