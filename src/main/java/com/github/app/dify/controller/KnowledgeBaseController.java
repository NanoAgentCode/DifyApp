package com.github.app.dify.controller;

import com.github.app.dify.req.CreateKnowledgeBaseReq;
import com.github.app.dify.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.resp.KnowledgeBaseResp;
import com.github.app.dify.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
/**
 * 知识库控制器
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 创建知识库
     */
    @Operation(summary = "创建知识库")
    @PostMapping
    public ResponseEntity<?> createKnowledgeBase(
            @Validated @RequestBody CreateKnowledgeBaseReq req,
            @RequestParam(required = false, defaultValue = "false") Boolean force,
            HttpServletRequest request) {
        logger.info("接收到创建知识库请求 - 名称: {}, 强制创建: {}", req.getName(), force);
        try {
            // 从request中获取用户信息（由JWT拦截器设置）
            Long userId = (Long) request.getAttribute("userId");
            String username = (String) request.getAttribute("username");
            Integer role = (Integer) request.getAttribute("role");
            
            KnowledgeBaseResp resp = knowledgeBaseService.createKnowledgeBase(req, userId, username, role, force);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("创建知识库失败", e);
            // 如果是重复名称错误，返回特殊错误信息
            if (e.getMessage() != null && e.getMessage().startsWith("DUPLICATE_NAME:")) {
                java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
                errorResponse.put("error", e.getMessage().substring("DUPLICATE_NAME:".length()));
                errorResponse.put("code", "DUPLICATE_NAME");
                return ResponseEntity.status(409) // 409 Conflict
                        .body(errorResponse);
            }
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "创建知识库失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 更新知识库
     */
    @Operation(summary = "更新知识库")
    @PutMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResp> updateKnowledgeBase(@PathVariable Long id, 
                                                                 @Validated @RequestBody UpdateKnowledgeBaseReq req) {
        try {
            KnowledgeBaseResp resp = knowledgeBaseService.updateKnowledgeBase(id, req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("更新知识库失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据ID获取知识库
     */
    @Operation(summary = "根据ID获取知识库")
    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeBaseResp> getKnowledgeBaseById(@PathVariable Long id) {
        try {
            KnowledgeBaseResp resp = knowledgeBaseService.getKnowledgeBaseById(id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取知识库失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除知识库
     */
    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable Long id) {
        try {
            knowledgeBaseService.deleteKnowledgeBase(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除知识库失败", e);
            return ResponseEntity.badRequest().build();
        }
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
        try {
            // 从request中获取用户信息（由JWT拦截器设置）
            Long currentUserId = (Long) request.getAttribute("userId");
            Integer userRole = (Integer) request.getAttribute("role");
            
            // 如果请求参数中没有userId，使用当前登录用户的ID
            if (userId == null && currentUserId != null) {
                userId = currentUserId;
            }
            
            // 如果指定了分页参数，使用分页接口
            if (page != null && pageSize != null && page > 0 && pageSize > 0) {
                com.github.app.dify.resp.PageResponse<KnowledgeBaseResp> pageResponse = 
                        knowledgeBaseService.listKnowledgeBasesWithPagination(
                                tenantId, status, keyword, vectorStoreType, userId, userRole, page, pageSize);
                return ResponseEntity.ok(pageResponse);
            } else {
                // 否则返回所有数据（兼容旧接口）
                List<KnowledgeBaseResp> resp = knowledgeBaseService.listKnowledgeBases(tenantId, status, keyword, userId, userRole);
                return ResponseEntity.ok(resp);
            }
        } catch (Exception e) {
            logger.error("获取知识库列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 生成知识库智能摘要
     */
    @Operation(summary = "生成知识库智能摘要")
    @PostMapping("/{id}/generate-summary")
    public ResponseEntity<?> generateSummary(
            @PathVariable Long id,
            @RequestParam(required = false) Long modelId,
            HttpServletRequest request) {
        try {
            logger.info("接收到生成知识库摘要请求 - 知识库ID: {}, 模型ID: {}", id, modelId);
            
            // 从request中获取用户信息（由JWT拦截器设置）
            Long userId = (Long) request.getAttribute("userId");
            Integer role = (Integer) request.getAttribute("role");
            
            // 验证用户权限：检查用户是否有权限访问该知识库
            KnowledgeBaseResp kb = knowledgeBaseService.getKnowledgeBaseById(id);
            boolean hasAccess = false;
            if (role != null && role == 1) {
                // 管理员可以访问所有知识库
                hasAccess = true;
            } else {
                // 普通用户只能访问公开的知识库或自己创建的知识库
                boolean isPublic = Boolean.TRUE.equals(kb.getIsPublic());
                boolean isOwner = userId != null && userId.equals(kb.getCreatorId());
                hasAccess = isPublic || isOwner;
            }
            
            if (!hasAccess) {
                return ResponseEntity.status(403).body(java.util.Map.of("error", "没有权限访问该知识库"));
            }
            
            String summary = knowledgeBaseService.generateSummary(id, modelId);
            return ResponseEntity.ok(java.util.Map.of("summary", summary));
        } catch (Exception e) {
            logger.error("生成知识库摘要失败 - 知识库ID: {}", id, e);
            java.util.Map<String, Object> errorResponse = new java.util.HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "生成摘要失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}