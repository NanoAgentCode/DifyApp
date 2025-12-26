package com.github.app.dify.documentreader.service;

import java.util.List;

/**
 * 文档解读RAG检索服务接口
 */
public interface DocumentReaderRetrievalService {
    
    /**
     * 检索相关文档chunks
     * @param documentId 文档ID
     * @param query 查询文本
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieve(Long documentId, String query);
    
    /**
     * 检索相关文档chunks（指定向量化模型ID）
     * @param documentId 文档ID
     * @param query 查询文本
     * @param embeddingModelId 向量化模型ID（可选，如果为null则使用默认模型）
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieve(Long documentId, String query, Long embeddingModelId);
    
    /**
     * 检索相关文档chunks（指定向量化模型ID和topK）
     * @param documentId 文档ID
     * @param query 查询文本
     * @param embeddingModelId 向量化模型ID（可选，如果为null则使用默认模型）
     * @param topK Top-K检索数量（可选，如果为null则使用全局配置）
     * @return 检索结果列表
     */
    List<RetrievalResult> retrieve(Long documentId, String query, Long embeddingModelId, Integer topK);
    
    /**
     * 检索结果
     */
    class RetrievalResult {
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

