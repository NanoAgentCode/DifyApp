package com.github.app.dify.documentreader.service;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.Map;

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
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param targetLang 目标语言
     * @param forceRetranslate 是否强制重新翻译（清除旧的翻译记录）
     */
    void translateDocument(Long documentId, Long userId, String targetLang, boolean forceRetranslate);
    
    /**
     * 获取文档翻译内容
     */
    String getDocumentTranslation(Long documentId, Long userId, String targetLang);
    
    /**
     * 获取文档翻译内容（懒加载模式，返回指定范围的翻译）
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param targetLang 目标语言
     * @param startSegment 起始分段索引（从0开始）
     * @param endSegment 结束分段索引（不包含）
     * @return 翻译内容
     */
    String getDocumentTranslationRange(Long documentId, Long userId, String targetLang, int startSegment, int endSegment);
    
    /**
     * 获取文档分段信息
     * @param documentId 文档ID
     * @param userId 用户ID
     * @return 分段信息（包含总段数、每段的起始位置等）
     */
    Map<String, Object> getDocumentSegments(Long documentId, Long userId);
    
    /**
     * 翻译指定分段（懒加载）
     * @param documentId 文档ID
     * @param userId 用户ID
     * @param targetLang 目标语言
     * @param segmentIndex 分段索引
     * @return 翻译后的内容
     */
    String translateDocumentSegment(Long documentId, Long userId, String targetLang, int segmentIndex);
    
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
    
    /**
     * 获取文档原文文本内容
     */
    String getDocumentText(Long documentId, Long userId);
}

