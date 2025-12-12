package com.github.app.dify.appknowledgebase.service;

import org.springframework.web.multipart.MultipartFile;
/**
 * 文档向量化服务接口
 */
public interface DocumentVectorizationService {
    
    /**
     * 异步向量化文档
     */
    void vectorizeDocumentAsync(Long knowledgeBaseId, Long documentId, MultipartFile file);
    
    /**
     * 重新索引文档
     */
    void reindexDocument(Long knowledgeBaseId, Long documentId);
    
    /**
     * 删除文档的所有向量
     */
    void deleteDocumentVectors(Long knowledgeBaseId, Long documentId);
}