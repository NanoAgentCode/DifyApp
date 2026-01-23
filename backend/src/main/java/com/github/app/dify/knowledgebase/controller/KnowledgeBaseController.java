package com.github.app.dify.knowledgebase.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.knowledgebase.req.CreateKnowledgeBaseReq;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseImportRequest;
import com.github.app.dify.knowledgebase.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseImportResult;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseResp;
import com.github.app.dify.knowledgebase.resp.ZipPreviewResult;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseMigrationService;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseService;
import com.github.app.dify.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;

/**
 * 知识库控制器
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController extends BaseController {
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    @Autowired
    private KnowledgeBaseMigrationService migrationService;
    
    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        return roleObj instanceof Integer && (Integer) roleObj == 1;
    }
    
    /**
     * 创建知识库
     */
    @UserAction(module = "知识库管理", actionType = "创建", description = "创建知识库")
    @Operation(summary = "创建知识库")
    @PostMapping
    public ResponseEntity<KnowledgeBaseResp> createKnowledgeBase(
            @Validated @RequestBody CreateKnowledgeBaseReq req,
            @RequestParam(required = false, defaultValue = "false") Boolean force,
            HttpServletRequest request) {
        logger.info("接收到创建知识库请求 - 名称: {}, 强制创建: {}", req.getName(), force);
        
        Long userId = getUserId(request);
        String username = getUsername(request);
        Object roleObj = request.getAttribute("role");
        Integer role = roleObj instanceof Integer ? (Integer) roleObj : null;
        
        try {
            KnowledgeBaseResp resp = knowledgeBaseService.createKnowledgeBase(req, userId, username, role, force);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            // 如果是重复名称错误，返回409 Conflict
            if (e.getMessage() != null && e.getMessage().startsWith("DUPLICATE_NAME:")) {
                String errorMsg = e.getMessage().substring("DUPLICATE_NAME:".length());
                throw new BusinessException(errorMsg, 409);
            }
            throw new BusinessException(e.getMessage() != null ? e.getMessage() : "创建知识库失败");
        }
    }
    
    /**
     * 更新知识库
     */
    @UserAction(module = "知识库管理", actionType = "更新", description = "更新知识库配置")
    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResp> updateKnowledgeBase(@PathVariable Long id,
                                                                 @Validated @RequestBody UpdateKnowledgeBaseReq req) {
        KnowledgeBaseResp resp = knowledgeBaseService.updateKnowledgeBase(id, req);
        if (resp == null) {
            throw new NotFoundException("知识库不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据ID获取知识库
     */
    @Operation(summary = "根据ID获取知识库")
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResp> getKnowledgeBaseById(@PathVariable Long id) {
        KnowledgeBaseResp resp = knowledgeBaseService.getKnowledgeBaseById(id);
        if (resp == null) {
            throw new NotFoundException("知识库不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除知识库
     */
    @UserAction(module = "知识库管理", actionType = "删除", description = "删除知识库")
    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取知识库列表
     */
    @Operation(summary = "获取知识库列表")
    @GetMapping
    public ResponseEntity<?> listKnowledgeBases(
            @RequestParam(required = false) Integer tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String vectorStoreType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getUserId(request);
        Object roleObj = request.getAttribute("role");
        Integer userRole = roleObj instanceof Integer ? (Integer) roleObj : null;
        
        // 如果请求参数中没有userId，使用当前登录用户的ID
        if (userId == null) {
            userId = currentUserId;
        }
        
        // 如果指定了分页参数，使用分页接口
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            com.github.app.dify.common.resp.PageResponse<KnowledgeBaseResp> pageResponse =
                    knowledgeBaseService.listKnowledgeBasesWithPagination(
                            tenantId, status, keyword, vectorStoreType, userId, userRole, page, pageSize);
            return ResponseEntity.ok(pageResponse);
        } else {
            // 否则返回所有数据（兼容旧接口）
            List<KnowledgeBaseResp> resp = knowledgeBaseService.listKnowledgeBases(tenantId, status, keyword, userId, userRole);
            return ResponseEntity.ok(resp);
        }
    }
    
    /**
     * 生成知识库智能摘要
     */
    @UserAction(module = "知识库管理", actionType = "生成摘要", description = "生成知识库智能摘要")
    @Operation(summary = "生成知识库智能摘要")
    @PostMapping("/{id}/generate-summary")
    public ResponseEntity<java.util.Map<String, String>> generateSummary(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelId,
            HttpServletRequest request) {
        logger.info("接收到生成知识库摘要请求 - 知识库ID: {}, 模型ID: {}", id, modelId);
        
        Long userId = getUserId(request);
        boolean isAdmin = isAdmin(request);
        
        // 验证用户权限：检查用户是否有权限访问该知识库
        KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(id);
        if (kb == null) {
            throw new NotFoundException("知识库不存在: " + id);
        }
        
        boolean hasAccess = false;
        if (isAdmin) {
            // 管理员可以访问所有知识库
            hasAccess = true;
        } else {
            // 普通用户只能访问公开的知识库或自己创建的知识库
            boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
            boolean isOwner = userId.equals(kb.getCreatorId());
            hasAccess = isPublic || isOwner;
        }
        
        if (!hasAccess) {
            throw new ForbiddenException("没有权限访问该知识库");
        }
        
        String summary = knowledgeBaseService.generateSummary(id, modelId);
        return ResponseEntity.ok(java.util.Map.of("summary", summary));
    }
    
    /**
     * 导出知识库
     */
    @UserAction(module = "知识库管理", actionType = "导出", description = "导出知识库")
    @Operation(summary = "导出知识库")
    @GetMapping("/{id}/export")
    public ResponseEntity<org.springframework.core.io.Resource> exportKnowledgeBase(
            @PathVariable Long id,
            HttpServletRequest request) {
        logger.info("接收到导出知识库请求 - 知识库ID: {}", id);
        
        Long userId = getUserId(request);
        boolean isAdmin = isAdmin(request);
        
        // 验证用户权限
        KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(id);
        if (kb == null) {
            throw new NotFoundException("知识库不存在: " + id);
        }
        
        boolean hasAccess = false;
        if (isAdmin) {
            hasAccess = true;
        } else {
            boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
            boolean isOwner = userId.equals(kb.getCreatorId());
            hasAccess = isPublic || isOwner;
        }
        
        if (!hasAccess) {
            throw new ForbiddenException("没有权限导出该知识库");
        }
        
        try {
            InputStream zipStream = migrationService.exportKnowledgeBase(id);
            String fileName = "knowledge-base-" + id + "-" + System.currentTimeMillis() + ".zip";
            
            // 将InputStream读取到字节数组，避免流被多次读取的问题
            byte[] zipBytes;
            try (java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                byte[] data = new byte[8192];
                int nRead;
                while ((nRead = zipStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                zipBytes = buffer.toByteArray();
            } finally {
                zipStream.close();
            }
            
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(zipBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipBytes.length)
                    .body(resource);
        } catch (Exception e) {
            logger.error("导出知识库失败 - 知识库ID: {}", id, e);
            throw new BusinessException("导出知识库失败: " + e.getMessage());
        }
    }
    
    /**
     * 预览ZIP文件内容
     */
    @Operation(summary = "预览ZIP文件内容")
    @PostMapping("/import/preview")
    public ResponseEntity<ZipPreviewResult> previewZipFile(
            @RequestParam("file") MultipartFile file) {
        logger.info("接收到预览ZIP文件请求 - 文件名: {}", file.getOriginalFilename());
        
        try {
            ZipPreviewResult result = migrationService.previewZipFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("预览ZIP文件失败", e);
            throw new BusinessException("预览ZIP文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 导入知识库
     */
    @UserAction(module = "知识库管理", actionType = "导入", description = "导入知识库")
    @Operation(summary = "导入知识库")
    @PostMapping("/import")
    public ResponseEntity<KnowledgeBaseImportResult> importKnowledgeBase(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseName") String knowledgeBaseName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String vectorStoreType,
            @RequestParam(required = false) Boolean isPublic,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Long embeddingModelId,
            HttpServletRequest request) {
        logger.info("接收到导入知识库请求 - 文件名: {}, 知识库名称: {}", 
                file.getOriginalFilename(), knowledgeBaseName);
        
        Long userId = getUserId(request);
        String username = getUsername(request);
        Object tenantIdObj = request.getAttribute("tenantId");
        Integer tenantId = tenantIdObj instanceof Integer ? (Integer) tenantIdObj : 1;
        
        // 构建导入请求
        KnowledgeBaseImportRequest importRequest = new KnowledgeBaseImportRequest();
        importRequest.setKnowledgeBaseName(knowledgeBaseName);
        importRequest.setDescription(description);
        importRequest.setVectorStoreType(vectorStoreType);
        importRequest.setIsPublic(isPublic);
        importRequest.setTopK(topK);
        importRequest.setEmbeddingModelId(embeddingModelId);
        
        try {
            KnowledgeBaseImportResult result = migrationService.importKnowledgeBase(
                    file, importRequest, userId, username, tenantId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("导入知识库失败", e);
            throw new BusinessException("导入知识库失败: " + e.getMessage());
        }
    }
}