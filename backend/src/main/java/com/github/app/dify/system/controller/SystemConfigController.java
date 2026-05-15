package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置接口（供前端主题、帮助等使用）
 * GET /api/system-config/value/{key}、GET /api/system-config/group/{group} 为公开接口，无需登录。
 * GET /api/system-config、GET /api/system-config/{key}、POST /api/system-config、DELETE /api/system-config/{key} 为管理端接口，需登录。
 */
@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController extends BaseController {

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

    /**
     * 获取所有未删除的配置（管理端列表）
     */
    @Operation(summary = "获取所有配置")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(HttpServletRequest request) {
        getUserId(request);
        List<SystemConfig> list = systemConfigService.getAllConfigs();
        List<Map<String, Object>> body = list.stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    /**
     * 分页查询配置（管理端列表）
     */
    @Operation(summary = "分页查询配置")
    @GetMapping("/page")
    public ResponseEntity<PageResponse<Map<String, Object>>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String configGroup,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        getUserId(request);
        PageResponse<SystemConfig> source = systemConfigService.getConfigsWithPagination(keyword, configGroup, page, pageSize);
        PageResponse<Map<String, Object>> body = new PageResponse<>(
                source.getContent().stream().map(this::toMap).collect(Collectors.toList()),
                source.getTotal(),
                source.getPage(),
                source.getPageSize()
        );
        body.setTotalPages(source.getTotalPages());
        return ResponseEntity.ok(body);
    }

    /**
     * 根据配置键获取完整配置（管理端）
     */
    @Operation(summary = "根据键获取完整配置")
    @GetMapping("/{configKey}")
    public ResponseEntity<Map<String, Object>> getByKey(@PathVariable String configKey, HttpServletRequest request) {
        getUserId(request);
        return systemConfigService.getConfigByKey(configKey)
                .map(c -> ResponseEntity.ok(toMap(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 新增或更新配置（管理端）
     */
    @Operation(summary = "新增或更新配置")
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveOrUpdate(
            @RequestBody SystemConfigRequest req,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String username = getUsername(request);
        SystemConfig config = new SystemConfig();
        config.setConfigKey(req.getConfigKey());
        config.setConfigValue(req.getConfigValue());
        config.setConfigGroup(req.getConfigGroup());
        config.setConfigType(req.getConfigType());
        config.setDescription(req.getDescription());
        SystemConfig saved = systemConfigService.saveOrUpdate(config, userId, username);
        return ResponseEntity.ok(toMap(saved));
    }

    /**
     * 按配置键软删除（管理端）
     */
    @Operation(summary = "删除配置")
    @DeleteMapping("/{configKey}")
    public ResponseEntity<Void> deleteByKey(@PathVariable String configKey, HttpServletRequest request) {
        getUserId(request);
        systemConfigService.deleteByKey(configKey);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(SystemConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("configKey", c.getConfigKey());
        m.put("configValue", c.getConfigValue());
        m.put("configGroup", c.getConfigGroup());
        m.put("description", c.getDescription());
        m.put("configType", c.getConfigType());
        m.put("createTime", c.getCreateTime());
        m.put("updateTime", c.getUpdateTime());
        return m;
    }
}
