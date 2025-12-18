package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.system.req.ModelConfigRequest;
import com.github.app.dify.system.req.TestModelConnectionRequest;
import com.github.app.dify.system.resp.ModelConfigResponse;
import com.github.app.dify.system.service.ModelConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 模型配置控制器
 */
@Tag(name = "模型配置管理")
@RestController
@RequestMapping("/api/models")
public class ModelConfigController extends BaseController {
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    /**
     * 获取模型配置
     */
    @Operation(summary = "获取模型配置")
    @GetMapping("/config")
    public ResponseEntity<ModelConfigResponse> getModelConfig(HttpServletRequest request) {
        ModelConfigResponse response = modelConfigService.getModelConfig();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 更新模型配置
     */
    @Operation(summary = "更新模型配置")
    @PutMapping("/config")
    public ResponseEntity<Object> updateModelConfig(
            @Valid @RequestBody ModelConfigRequest request,
            HttpServletRequest httpRequest) {
        Object result = modelConfigService.updateModelConfig(request);
        if (result == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试模型连接
     */
    @Operation(summary = "测试模型连接")
    @PostMapping("/test")
    public ResponseEntity<Void> testModelConnection(
            @Valid @RequestBody TestModelConnectionRequest request,
            HttpServletRequest httpRequest) {
        modelConfigService.testModelConnection(request);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取可用的问答模型列表（用于智能问答）
     */
    @Operation(summary = "获取可用的问答模型列表")
    @GetMapping("/qa/available")
    public ResponseEntity<Object> getAvailableQAModels(HttpServletRequest request) {
        return ResponseEntity.ok(modelConfigService.getAvailableQAModels("chat"));
    }
    
    /**
     * 获取可用的问答模型列表（用于知识库问答）
     */
    @Operation(summary = "获取可用的问答模型列表（用于知识库问答）")
    @GetMapping("/qa/available/rag")
    public ResponseEntity<Object> getAvailableQAModelsForRAG(HttpServletRequest request) {
        return ResponseEntity.ok(modelConfigService.getAvailableQAModels("rag"));
    }
}