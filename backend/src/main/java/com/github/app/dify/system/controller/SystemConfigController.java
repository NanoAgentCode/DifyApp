package com.github.app.dify.system.controller;

import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置接口（供前端主题、帮助等使用）
 * GET /api/system-config/value/{key}、GET /api/system-config/group/{group} 为公开接口，无需登录。
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    /**
     * 根据配置键获取配置值（前端全局主题等使用）
     * 返回 { configValue: "..." }，与前端 getConfigValue / globalTheme 约定一致。
     */
    @Operation(summary = "根据键获取配置值")
    @GetMapping("/value/{configKey}")
    public ResponseEntity<Map<String, String>> getValue(@PathVariable String configKey) {
        String value = systemConfigService.getConfigValue(configKey);
        Map<String, String> body = new HashMap<>();
        body.put("configValue", value);
        return ResponseEntity.ok(body);
    }

    /**
     * 根据配置分组获取配置列表（前端帮助等使用）
     */
    @Operation(summary = "根据分组获取配置列表")
    @GetMapping("/group/{configGroup}")
    public ResponseEntity<List<Map<String, Object>>> getGroup(@PathVariable String configGroup) {
        List<SystemConfig> list = systemConfigService.getConfigsByGroup(configGroup);
        List<Map<String, Object>> body = list.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toMap(SystemConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("configKey", c.getConfigKey());
        m.put("configValue", c.getConfigValue());
        m.put("configGroup", c.getConfigGroup());
        m.put("description", c.getDescription());
        m.put("configType", c.getConfigType());
        return m;
    }
}
