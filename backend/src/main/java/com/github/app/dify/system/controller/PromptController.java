package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.system.req.CreatePromptReq;
import com.github.app.dify.system.req.UpdatePromptReq;
import com.github.app.dify.system.resp.PromptResp;
import com.github.app.dify.system.service.PromptService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 提示词控制器
 */
@Tag(name = "提示词管理")
@RestController
@RequestMapping("/api/prompts")
public class PromptController extends BaseController {
    
    @Autowired
    private PromptService promptService;
    
    /**
     * 创建提示词
     */
    @Operation(summary = "创建提示词")
    @PostMapping
    public ResponseEntity<PromptResp> createPrompt(@Validated @RequestBody CreatePromptReq req) {
        logger.info("接收到创建提示词请求 - 标题: {}", req.getTitle());
        PromptResp resp = promptService.createPrompt(req);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 更新提示词
     */
    @Operation(summary = "更新提示词")
    @PutMapping("/{id}")
    public ResponseEntity<PromptResp> updatePrompt(
            @PathVariable Long id,
            @Validated @RequestBody UpdatePromptReq req) {
        PromptResp resp = promptService.updatePrompt(id, req);
        if (resp == null) {
            throw new NotFoundException("提示词不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据ID获取提示词
     */
    @Operation(summary = "根据ID获取提示词")
    @GetMapping("/{id}")
    public ResponseEntity<PromptResp> getPromptById(@PathVariable Long id) {
        PromptResp resp = promptService.getPromptById(id);
        if (resp == null) {
            throw new NotFoundException("提示词不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除提示词
     */
    @Operation(summary = "删除提示词")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrompt(@PathVariable Long id) {
        promptService.deletePrompt(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取提示词列表
     */
    @Operation(summary = "获取提示词列表")
    @GetMapping
    public ResponseEntity<List<PromptResp>> listPrompts(
            @RequestParam(required = false) String keyword) {
        List<PromptResp> prompts = promptService.listPrompts(keyword);
        return ResponseEntity.ok(prompts);
    }
}
