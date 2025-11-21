package com.github.app.dify.controller;

import com.github.app.dify.req.CreateKnowledgeBaseReq;
import com.github.app.dify.req.UpdateKnowledgeBaseReq;
import com.github.app.dify.resp.KnowledgeBaseResp;
import com.github.app.dify.service.KnowledgeBaseService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 知识库控制器
 */
@Api(tags = "知识库管理")
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);
    
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 创建知识库
     */
    @ApiOperation("创建知识库")
    @PostMapping
    public ResponseEntity<KnowledgeBaseResp> createKnowledgeBase(
            @Validated @RequestBody CreateKnowledgeBaseReq req,
            HttpServletRequest request) {
        logger.info("接收到创建知识库请求 - 名称: {}", req.getName());
        try {
            // 从request中获取用户信息（由JWT拦截器设置）
            Long userId = (Long) request.getAttribute("userId");
            String username = (String) request.getAttribute("username");
            Integer role = (Integer) request.getAttribute("role");
            
            KnowledgeBaseResp resp = knowledgeBaseService.createKnowledgeBase(req, userId, username, role);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("创建知识库失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 更新知识库
     */
    @ApiOperation("更新知识库")
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
    @ApiOperation("根据ID获取知识库")
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
    @ApiOperation("删除知识库")
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
    @ApiOperation("获取知识库列表")
    @GetMapping
    public ResponseEntity<List<KnowledgeBaseResp>> listKnowledgeBases(
            @RequestParam(required = false) Integer tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        try {
            // 从request中获取用户信息（由JWT拦截器设置）
            Long currentUserId = (Long) request.getAttribute("userId");
            Integer userRole = (Integer) request.getAttribute("role");
            
            // 如果请求参数中没有userId，使用当前登录用户的ID
            if (userId == null && currentUserId != null) {
                userId = currentUserId;
            }
            
            List<KnowledgeBaseResp> resp = knowledgeBaseService.listKnowledgeBases(tenantId, status, keyword, userId, userRole);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取知识库列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

