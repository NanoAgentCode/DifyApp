package com.github.app.dify.appknowledgebase.controller;

import com.github.app.dify.appknowledgebase.req.TestVectorDatabaseConnectionRequest;
import com.github.app.dify.appknowledgebase.req.VectorDatabaseRequest;
import com.github.app.dify.appknowledgebase.resp.VectorDatabaseResp;
import com.github.app.dify.appknowledgebase.service.VectorDatabaseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class VectorDatabaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorDatabaseController.class);
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    /**
     * 获取所有向量数据库配置
     */
    @Operation(summary = "获取所有向量数据库配置")
    @GetMapping
    public ResponseEntity<List<VectorDatabaseResp>> getAllConfigs(HttpServletRequest request) {
        try {
            List<VectorDatabaseResp> configs = vectorDatabaseService.getAllConfigs();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            logger.error("获取向量数据库配置列表失败", e);
            throw new RuntimeException("获取向量数据库配置列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据类型获取配置列表
     */
    @Operation(summary = "根据类型获取配置列表")
    @GetMapping("/type/{type}")
    public ResponseEntity<List<VectorDatabaseResp>> getConfigsByType(
            @PathVariable String type,
            HttpServletRequest request) {
        try {
            List<VectorDatabaseResp> configs = vectorDatabaseService.getConfigsByType(type);
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            logger.error("根据类型获取向量数据库配置列表失败", e);
            throw new RuntimeException("根据类型获取向量数据库配置列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新配置
     */
    @Operation(summary = "更新向量数据库配置")
    @PutMapping
    public ResponseEntity<?> updateConfig(
            @Valid @RequestBody VectorDatabaseRequest request,
            HttpServletRequest httpRequest) {
        try {
            Object result = vectorDatabaseService.updateConfig(request);
            if (result == null) {
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("更新向量数据库配置失败", e);
            throw new RuntimeException("更新向量数据库配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试连接
     */
    @Operation(summary = "测试向量数据库连接")
    @PostMapping("/test")
    public ResponseEntity<?> testConnection(
            @Valid @RequestBody TestVectorDatabaseConnectionRequest request,
            HttpServletRequest httpRequest) {
        try {
            vectorDatabaseService.testConnection(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("测试向量数据库连接失败", e);
            throw new RuntimeException("测试连接失败: " + e.getMessage());
        }
    }
}