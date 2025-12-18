package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.system.req.UpdateSystemConfigReq;
import com.github.app.dify.system.resp.SystemConfigResp;
import com.github.app.dify.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 系统配置控制器
 */
@Tag(name = "系统配置管理")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController extends BaseController {
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    /**
     * 根据配置键获取配置值
     */
    @Operation(summary = "根据配置键获取配置值")
    @GetMapping("/value/{configKey}")
    public ResponseEntity<SystemConfigResp> getConfigValue(@PathVariable String configKey) {
        String value = systemConfigService.getConfigValue(configKey);
        if (value == null) {
            throw new NotFoundException("配置不存在: " + configKey);
        }
        SystemConfigResp resp = new SystemConfigResp();
        resp.setConfigKey(configKey);
        resp.setConfigValue(value);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据配置键获取配置（完整信息）
     */
    @Operation(summary = "根据配置键获取配置")
    @GetMapping("/{configKey}")
    public ResponseEntity<SystemConfigResp> getConfigByKey(@PathVariable String configKey) {
        SystemConfigResp resp = systemConfigService.getConfigByKey(configKey);
        if (resp == null) {
            throw new NotFoundException("配置不存在: " + configKey);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据配置分组获取配置列表
     */
    @Operation(summary = "根据配置分组获取配置列表")
    @GetMapping("/group/{configGroup}")
    public ResponseEntity<List<SystemConfigResp>> getConfigsByGroup(@PathVariable String configGroup) {
        List<SystemConfigResp> resp = systemConfigService.getConfigsByGroup(configGroup);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 获取所有配置
     */
    @Operation(summary = "获取所有配置")
    @GetMapping
    public ResponseEntity<List<SystemConfigResp>> getAllConfigs() {
        List<SystemConfigResp> resp = systemConfigService.getAllConfigs();
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 设置或更新配置
     */
    @Operation(summary = "设置或更新配置")
    @PostMapping
    public ResponseEntity<SystemConfigResp> setOrUpdateConfig(
            @Validated @RequestBody UpdateSystemConfigReq req,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String username = getUsername(request);
        SystemConfigResp resp = systemConfigService.setOrUpdateConfig(req, userId, username);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除配置
     */
    @Operation(summary = "删除配置")
    @DeleteMapping("/{configKey}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String configKey) {
        systemConfigService.deleteConfig(configKey);
        return ResponseEntity.ok().build();
    }
}

