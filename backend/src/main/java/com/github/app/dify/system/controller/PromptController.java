package com.github.app.dify.system.controller;

import com.github.app.dify.system.req.CreatePromptReq;
import com.github.app.dify.system.req.UpdatePromptReq;
import com.github.app.dify.system.resp.PromptResp;
import com.github.app.dify.system.service.PromptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词控制器
 */
@Tag(name = "提示词管理")
@RestController
@RequestMapping("/api/prompts")
public class PromptController {
    
    private static final Logger logger = LoggerFactory.getLogger(PromptController.class);
    
    @Autowired
    private PromptService promptService;
    
    /**
     * 创建提示词
     */
    @Operation(summary = "创建提示词")
    @PostMapping
    public ResponseEntity<?> createPrompt(@Validated @RequestBody CreatePromptReq req) {
        logger.info("接收到创建提示词请求 - 标题: {}", req.getTitle());
        try {
            PromptResp resp = promptService.createPrompt(req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("创建提示词失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "创建提示词失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 更新提示词
     */
    @Operation(summary = "更新提示词")
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePrompt(
            @PathVariable Long id,
            @Validated @RequestBody UpdatePromptReq req) {
        try {
            PromptResp resp = promptService.updatePrompt(id, req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("更新提示词失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "更新提示词失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 根据ID获取提示词
     */
    @Operation(summary = "根据ID获取提示词")
    @GetMapping("/{id}")
    public ResponseEntity<PromptResp> getPromptById(@PathVariable Long id) {
        try {
            PromptResp resp = promptService.getPromptById(id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取提示词失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除提示词
     */
    @Operation(summary = "删除提示词")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePrompt(@PathVariable Long id) {
        try {
            promptService.deletePrompt(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除提示词失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "删除提示词失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 获取提示词列表
     */
    @Operation(summary = "获取提示词列表")
    @GetMapping
    public ResponseEntity<List<PromptResp>> listPrompts(
            @RequestParam(required = false) String keyword) {
        try {
            List<PromptResp> prompts = promptService.listPrompts(keyword);
            return ResponseEntity.ok(prompts);
        } catch (Exception e) {
            logger.error("获取提示词列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
