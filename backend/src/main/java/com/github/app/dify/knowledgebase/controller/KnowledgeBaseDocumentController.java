package com.github.app.dify.knowledgebase.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseDocumentResp;
import com.github.app.dify.knowledgebase.service.DocumentVectorizationService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.List;

/**
 * 知识库文档控制器
 */
@Tag(name = "知识库文档管理")
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/documents")
public class KnowledgeBaseDocumentController extends BaseController {
    
    @Autowired
    private KnowledgeBaseDocumentService documentService;
    
    @Autowired(required = false)
    private DocumentVectorizationService documentVectorizationService;
    
    /**
     * 上传文档
     */
    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public ResponseEntity<KnowledgeBaseDocumentResp> uploadDocument(
            @PathVariable Long kbId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadUser", required = false) String uploadUser,
            @RequestParam(value = "tenantId", required = false) Integer tenantId) {
        long startTime = System.currentTimeMillis();
        logger.info("=== 开始处理上传文档请求 ===");
        logger.info("知识库ID: {}, 文件名: {}, 文件大小: {} 字节 ({} MB), 文件类型: {}, 上传用户: {}, 租户ID: {}", 
                kbId, 
                file.getOriginalFilename(), 
                file.getSize(),
                String.format("%.2f", file.getSize() / 1024.0 / 1024.0),
                file.getContentType(),
                uploadUser != null ? uploadUser : "未指定",
                tenantId != null ? tenantId : "未指定");
        KnowledgeBaseDocumentResp resp = documentService.uploadDocument(kbId, file, uploadUser, tenantId);
        long duration = System.currentTimeMillis() - startTime;
        logger.info("=== 上传文档成功 ===");
        logger.info("知识库ID: {}, 文档ID: {}, 文件名: {}, 耗时: {} 毫秒", 
                kbId, resp.getId(), resp.getOriginalFileName(), duration);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除文档
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        documentService.deleteDocument(kbId, docId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取文档列表
     */
    @Operation(summary = "获取文档列表")
    @GetMapping
    public ResponseEntity<?> listDocuments(
            @PathVariable Long kbId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer vectorizedStatus,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        // 如果指定了分页参数，使用分页接口
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            PageResponse<KnowledgeBaseDocumentResp> pageResponse = documentService.listDocumentsWithPagination(
                    kbId, keyword, vectorizedStatus, fileType, page, pageSize);
            return ResponseEntity.ok(pageResponse);
        } else {
            // 否则返回所有数据（兼容旧接口）
            List<KnowledgeBaseDocumentResp> resp = documentService.listDocuments(kbId);
            return ResponseEntity.ok(resp);
        }
    }
    
    /**
     * 获取文档详情
     */
    @Operation(summary = "获取文档详情")
    @GetMapping("/{docId}")
    public ResponseEntity<KnowledgeBaseDocumentResp> getDocumentById(
            @PathVariable Long kbId, 
            @PathVariable Long docId) {
        KnowledgeBaseDocumentResp resp = documentService.getDocumentById(kbId, docId);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 下载文档
     */
    @Operation(summary = "下载文档")
    @GetMapping("/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable Long kbId, 
            @PathVariable Long docId) {
        KnowledgeBaseDocumentResp document = documentService.getDocumentById(kbId, docId);
        InputStream inputStream = documentService.downloadDocument(kbId, docId);
        
        InputStreamResource resource = new InputStreamResource(inputStream);
        
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + document.getOriginalFileName() + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, 
                document.getMimeType() != null ? document.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(document.getFileSize())
                .body(resource);
    }
    
    /**
     * 重新向量化文档
     */
    @Operation(summary = "重新向量化文档")
    @PostMapping("/{docId}/reindex")
    public ResponseEntity<Void> reindexDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        long startTime = System.currentTimeMillis();
        logger.info("=== 开始处理重新向量化文档请求 ===");
        logger.info("知识库ID: {}, 文档ID: {}", kbId, docId);
        
        if (documentVectorizationService == null) {
            logger.warn("DocumentVectorizationService未配置，无法重新向量化 - 知识库ID: {}, 文档ID: {}", kbId, docId);
            throw new BusinessException("向量化服务未配置");
        }
        
        // 验证文档是否存在
        logger.debug("验证文档是否存在 - 知识库ID: {}, 文档ID: {}", kbId, docId);
        KnowledgeBaseDocumentResp document = documentService.getDocumentById(kbId, docId);
        
        logger.info("文档信息 - 知识库ID: {}, 文档ID: {}, 文件名: {}, 当前向量化状态: {}, 文件大小: {} 字节", 
                kbId, docId, document.getOriginalFileName(), 
                document.getVectorizedStatus() != null ? document.getVectorizedStatus() : "未知",
                document.getFileSize());
        
        // 异步重新向量化
        logger.info("提交重新向量化任务到异步队列 - 知识库ID: {}, 文档ID: {}", kbId, docId);
        documentVectorizationService.reindexDocument(kbId, docId);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("=== 重新向量化文档任务已提交 ===");
        logger.info("知识库ID: {}, 文档ID: {}, 文件名: {}, 请求处理耗时: {} 毫秒", 
                kbId, docId, document.getOriginalFileName(), duration);
        return ResponseEntity.ok().build();
    }
}