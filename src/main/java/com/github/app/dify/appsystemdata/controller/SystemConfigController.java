package com.github.app.dify.appsystemdata.controller;

import com.github.app.dify.appsystemdata.req.UpdateSystemConfigReq;
import com.github.app.dify.appsystemdata.resp.SystemConfigResp;
import com.github.app.dify.appsystemdata.service.SystemConfigService;
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
import java.util.Map;
import java.util.HashMap;

/**
 * 系统配置控制器
 */
@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigController.class);
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    /**
     * 根据配置键获取配置值
     */
    @Operation(summary = "根据配置键获取配置值")
    @GetMapping("/value/{configKey}")
    public ResponseEntity<Map<String, Object>> getConfigValue(@PathVariable String configKey) {
        try {
            String value = systemConfigService.getConfigValue(configKey);
            Map<String, Object> response = new HashMap<>();
            response.put("configKey", configKey);
            response.put("configValue", value);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取配置值失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据配置键获取配置（完整信息）
     */
    @Operation(summary = "根据配置键获取配置")
    @GetMapping("/{configKey}")
    public ResponseEntity<SystemConfigResp> getConfigByKey(@PathVariable String configKey) {
        try {
            SystemConfigResp resp = systemConfigService.getConfigByKey(configKey);
            if (resp != null) {
                return ResponseEntity.ok(resp);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            logger.error("获取配置失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据配置分组获取配置列表
     */
    @Operation(summary = "根据配置分组获取配置列表")
    @GetMapping("/group/{configGroup}")
    public ResponseEntity<List<SystemConfigResp>> getConfigsByGroup(@PathVariable String configGroup) {
        try {
            List<SystemConfigResp> resp = systemConfigService.getConfigsByGroup(configGroup);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取配置列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取所有配置
     */
    @Operation(summary = "获取所有配置")
    @GetMapping
    public ResponseEntity<List<SystemConfigResp>> getAllConfigs() {
        try {
            List<SystemConfigResp> resp = systemConfigService.getAllConfigs();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取所有配置失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 设置或更新配置
     */
    @Operation(summary = "设置或更新配置")
    @PostMapping
    public ResponseEntity<SystemConfigResp> setOrUpdateConfig(
            @Validated @RequestBody UpdateSystemConfigReq req,
            HttpServletRequest request) {
        try {
            // 从request中获取用户信息（由JWT拦截器设置）
            Long userId = (Long) request.getAttribute("userId");
            String username = (String) request.getAttribute("username");
            
            if (userId == null || username == null) {
                return ResponseEntity.status(401).build();
            }
            
            SystemConfigResp resp = systemConfigService.setOrUpdateConfig(req, userId, username);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("设置或更新配置失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "设置或更新配置失败");
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    /**
     * 删除配置
     */
    @Operation(summary = "删除配置")
    @DeleteMapping("/{configKey}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String configKey) {
        try {
            systemConfigService.deleteConfig(configKey);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除配置失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

