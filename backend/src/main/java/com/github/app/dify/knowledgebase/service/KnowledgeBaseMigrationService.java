package com.github.app.dify.knowledgebase.service;

import com.github.app.dify.knowledgebase.req.KnowledgeBaseImportRequest;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseImportResult;
import com.github.app.dify.knowledgebase.resp.ZipPreviewResult;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

/**
 * 知识库迁移服务接口
 */
public interface KnowledgeBaseMigrationService {
    
    /**
     * 导出知识库为ZIP文件
     * @param knowledgeBaseId 知识库ID
     * @return ZIP文件输入流
     * @throws Exception 导出失败时抛出异常
     */
    InputStream exportKnowledgeBase(Long knowledgeBaseId) throws Exception;
    
    /**
     * 导入知识库从ZIP文件
     * @param zipFile ZIP文件
     * @param request 导入请求（包含知识库信息）
     * @param userId 用户ID
     * @param username 用户名
     * @param tenantId 租户ID
     * @return 导入结果
     * @throws Exception 导入失败时抛出异常
     */
    KnowledgeBaseImportResult importKnowledgeBase(
        MultipartFile zipFile, 
        KnowledgeBaseImportRequest request,
        Long userId, 
        String username, 
        Integer tenantId
    ) throws Exception;
    
    /**
     * 预览ZIP文件内容
     * @param zipFile ZIP文件
     * @return 预览结果（文件列表）
     * @throws Exception 预览失败时抛出异常
     */
    ZipPreviewResult previewZipFile(MultipartFile zipFile) throws Exception;
}
