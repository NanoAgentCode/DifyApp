package com.github.app.dify.knowledgebase.controller;

import com.github.app.dify.knowledgebase.resp.KnowledgeBaseDocumentResp;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class KnowledgeBaseDocumentController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseDocumentController.class);
    
    @Autowired
    private KnowledgeBaseDocumentService documentService;
    
    @Autowired(required = false)
    private com.github.app.dify.knowledgebase.service.DocumentVectorizationService documentVectorizationService;
    
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
        try {
            KnowledgeBaseDocumentResp resp = documentService.uploadDocument(kbId, file, uploadUser, tenantId);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("=== 上传文档成功 ===");
            logger.info("知识库ID: {}, 文档ID: {}, 文件名: {}, 耗时: {} 毫秒", 
                    kbId, resp.getId(), resp.getOriginalFileName(), duration);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("=== 上传文档失败 ===");
            logger.error("知识库ID: {}, 文件名: {}, 耗时: {} 毫秒, 错误信息: {}", 
                    kbId, file.getOriginalFilename(), duration, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除文档
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long kbId, @PathVariable Long docId) {
        try {
            documentService.deleteDocument(kbId, docId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除文档失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取文档列表
     */
    @Operation(summary = "获取文档列表")
    @GetMapping
    public ResponseEntity<List<KnowledgeBaseDocumentResp>> listDocuments(@PathVariable Long kbId) {
        try {
            List<KnowledgeBaseDocumentResp> resp = documentService.listDocuments(kbId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取文档列表失败", e);
            return ResponseEntity.badRequest().build();
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
        try {
            KnowledgeBaseDocumentResp resp = documentService.getDocumentById(kbId, docId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取文档详情失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 下载文档
     */
    @Operation(summary = "下载文档")
    @GetMapping("/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable Long kbId, 
            @PathVariable Long docId) {
        try {
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
        } catch (Exception e) {
            logger.error("下载文档失败", e);
            return ResponseEntity.notFound().build();
        }
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
        
        try {
            if (documentVectorizationService == null) {
                logger.warn("DocumentVectorizationService未配置，无法重新向量化 - 知识库ID: {}, 文档ID: {}", kbId, docId);
                return ResponseEntity.badRequest().build();
            }
            
            // 验证文档是否存在
            logger.debug("验证文档是否存在 - 知识库ID: {}, 文档ID: {}", kbId, docId);
            KnowledgeBaseDocumentResp document = documentService.getDocumentById(kbId, docId);
            if (document == null) {
                logger.warn("文档不存在 - 知识库ID: {}, 文档ID: {}", kbId, docId);
                return ResponseEntity.notFound().build();
            }
            
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
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("=== 重新向量化文档请求处理失败 ===");
            logger.error("知识库ID: {}, 文档ID: {}, 请求处理耗时: {} 毫秒, 错误信息: {}", 
                    kbId, docId, duration, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
}