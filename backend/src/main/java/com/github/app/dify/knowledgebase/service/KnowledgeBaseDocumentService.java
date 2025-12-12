package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseDocumentResp;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.List;
/**
 * 知识库文档服务接口
 */
public interface KnowledgeBaseDocumentService {
    
    /**
     * 上传文档
     */
    KnowledgeBaseDocumentResp uploadDocument(Long knowledgeBaseId, MultipartFile file, String uploadUser, Integer tenantId);
    
    /**
     * 删除文档
     */
    void deleteDocument(Long knowledgeBaseId, Long documentId);
    
    /**
     * 根据ID获取文档
     */
    KnowledgeBaseDocumentResp getDocumentById(Long knowledgeBaseId, Long documentId);
    
    /**
     * 获取文档列表
     */
    List<KnowledgeBaseDocumentResp> listDocuments(Long knowledgeBaseId);
    
    /**
     * 获取文档列表（分页，支持搜索和过滤）
     */
    PageResponse<KnowledgeBaseDocumentResp> listDocumentsWithPagination(
            Long knowledgeBaseId, 
            String keyword, 
            Integer vectorizedStatus, 
            String fileType, 
            int page, 
            int pageSize);
    
    /**
     * 获取文档数量
     */
    Long getDocumentCount(Long knowledgeBaseId);
    
    /**
     * 下载文档
     */
    InputStream downloadDocument(Long knowledgeBaseId, Long documentId);
}