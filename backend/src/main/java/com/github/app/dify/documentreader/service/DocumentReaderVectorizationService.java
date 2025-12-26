package com.github.app.dify.documentreader.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解读向量化服务接口
 */
public interface DocumentReaderVectorizationService {
    
    /**
     * 异步向量化文档
     */
    void vectorizeDocumentAsync(Long documentId, MultipartFile file);
    
    /**
     * 重新索引文档
     */
    void reindexDocument(Long documentId);
    
    /**
     * 删除文档的所有向量
     */
    void deleteDocumentVectors(Long documentId);
}

