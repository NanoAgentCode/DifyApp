package com.github.app.dify.knowledgebase.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.knowledgebase.req.TestVectorDatabaseConnectionRequest;
import com.github.app.dify.knowledgebase.req.VectorDatabaseRequest;
import com.github.app.dify.knowledgebase.resp.VectorDatabaseResp;
import com.github.app.dify.knowledgebase.service.VectorDatabaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 向量数据库配置控制器
 */
@Tag(name = "向量数据库配置管理")
@RestController
@RequestMapping("/api/vector-databases")
public class VectorDatabaseController extends BaseController {
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    /**
     * 获取所有向量数据库配置
     */
    @Operation(summary = "获取所有向量数据库配置")
    @GetMapping
    public ResponseEntity<List<VectorDatabaseResp>> getAllConfigs(HttpServletRequest request) {
        List<VectorDatabaseResp> configs = vectorDatabaseService.getAllConfigs();
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 根据类型获取配置列表
     */
    @Operation(summary = "根据类型获取配置列表")
    @GetMapping("/type/{type}")
    public ResponseEntity<List<VectorDatabaseResp>> getConfigsByType(
            @PathVariable String type,
            HttpServletRequest request) {
        List<VectorDatabaseResp> configs = vectorDatabaseService.getConfigsByType(type);
        return ResponseEntity.ok(configs);
    }
    
    /**
     * 更新配置
     */
    @Operation(summary = "更新向量数据库配置")
    @PutMapping
    public ResponseEntity<Object> updateConfig(
            @Valid @RequestBody VectorDatabaseRequest request,
            HttpServletRequest httpRequest) {
        Object result = vectorDatabaseService.updateConfig(request);
        if (result == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试连接
     */
    @Operation(summary = "测试向量数据库连接")
    @PostMapping("/test")
    public ResponseEntity<Void> testConnection(
            @Valid @RequestBody TestVectorDatabaseConnectionRequest request,
            HttpServletRequest httpRequest) {
        vectorDatabaseService.testConnection(request);
        return ResponseEntity.ok().build();
    }
}