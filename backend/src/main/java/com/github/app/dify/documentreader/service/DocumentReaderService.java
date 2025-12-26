package com.github.app.dify.documentreader.service;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

/**
 * 文档解读服务接口
 */
public interface DocumentReaderService {
    
    /**
     * 上传文档
     */
    DocumentReaderResp uploadDocument(MultipartFile file, Long userId);
    
    /**
     * 删除文档
     */
    void deleteDocument(Long documentId, Long userId);
    
    /**
     * 根据ID获取文档
     */
    DocumentReaderResp getDocumentById(Long documentId, Long userId);
    
    /**
     * 获取文档列表（分页，支持搜索和过滤）
     */
    PageResponse<DocumentReaderResp> listDocumentsWithPagination(
            Long userId,
            String keyword,
            String fileType,
            int page,
            int pageSize);
    
    /**
     * 获取文档内容（用于显示）
     */
    InputStream getDocumentContent(Long documentId, Long userId, Integer page);
    
    /**
     * 获取文档导读
     */
    String getDocumentGuide(Long documentId, Long userId);
    
    /**
     * 保存文档导读
     */
    void saveDocumentGuide(Long documentId, Long userId, String content);
    
    /**
     * 生成文档导读（使用大模型）
     */
    String generateDocumentGuide(Long documentId, Long userId, Long modelId);
    
    /**
     * 翻译文档
     */
    void translateDocument(Long documentId, Long userId, String targetLang);
    
    /**
     * 获取文档翻译内容
     */
    String getDocumentTranslation(Long documentId, Long userId, String targetLang);
    
    /**
     * 保存文档翻译内容
     */
    void saveDocumentTranslation(Long documentId, Long userId, String targetLang, String content);
    
    /**
     * 获取文档脑图
     */
    String getDocumentMindMap(Long documentId, Long userId);
    
    /**
     * 保存文档脑图
     */
    void saveDocumentMindMap(Long documentId, Long userId, String mindMapData);
    
    /**
     * 生成文档脑图（使用大模型）
     */
    String generateDocumentMindMap(Long documentId, Long userId, Long modelId);
    
    /**
     * 获取文档笔记
     */
    String getDocumentNotes(Long documentId, Long userId);
    
    /**
     * 保存文档笔记
     */
    void saveDocumentNotes(Long documentId, Long userId, String content);
}

