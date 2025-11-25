package com.github.app.dify.controller;

import com.github.app.dify.req.ModelConfigRequest;
import com.github.app.dify.req.TestModelConnectionRequest;
import com.github.app.dify.resp.ModelConfigResponse;
import com.github.app.dify.service.ModelConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 模型配置控制器
 */
@Api(tags = "模型配置管理")
@RestController
@RequestMapping("/api/models")
public class ModelConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(ModelConfigController.class);
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    /**
     * 获取模型配置
     */
    @ApiOperation("获取模型配置")
    @GetMapping("/config")
    public ResponseEntity<ModelConfigResponse> getModelConfig(HttpServletRequest request) {
        try {
            ModelConfigResponse response = modelConfigService.getModelConfig();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取模型配置失败", e);
            throw new RuntimeException("获取模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新模型配置
     */
    @ApiOperation("更新模型配置")
    @PutMapping("/config")
    public ResponseEntity<?> updateModelConfig(
            @Valid @RequestBody ModelConfigRequest request,
            HttpServletRequest httpRequest) {
        try {
            Object result = modelConfigService.updateModelConfig(request);
            if (result == null) {
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新模型配置失败", e);
            throw new RuntimeException("更新模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试模型连接
     */
    @ApiOperation("测试模型连接")
    @PostMapping("/test")
    public ResponseEntity<?> testModelConnection(
            @Valid @RequestBody TestModelConnectionRequest request,
            HttpServletRequest httpRequest) {
        try {
            modelConfigService.testModelConnection(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("测试模型连接失败", e);
            throw new RuntimeException("测试模型连接失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取可用的问答模型列表（用于智能问答）
     */
    @ApiOperation("获取可用的问答模型列表")
    @GetMapping("/qa/available")
    public ResponseEntity<?> getAvailableQAModels(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(modelConfigService.getAvailableQAModels("chat"));
        } catch (Exception e) {
            logger.error("获取可用问答模型列表失败", e);
            throw new RuntimeException("获取可用问答模型列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取可用的问答模型列表（用于知识库问答）
     */
    @ApiOperation("获取可用的问答模型列表（用于知识库问答）")
    @GetMapping("/qa/available/rag")
    public ResponseEntity<?> getAvailableQAModelsForRAG(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(modelConfigService.getAvailableQAModels("rag"));
        } catch (Exception e) {
            logger.error("获取可用问答模型列表失败", e);
            throw new RuntimeException("获取可用问答模型列表失败: " + e.getMessage());
        }
    }
}

