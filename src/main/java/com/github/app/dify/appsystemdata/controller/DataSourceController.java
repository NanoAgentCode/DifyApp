package com.github.app.dify.appsystemdata.controller;

import com.github.app.dify.appsystemdata.req.CreateDataSourceReq;
import com.github.app.dify.appsystemdata.req.UpdateDataSourceReq;
import com.github.app.dify.appsystemdata.resp.DataSourceResp;
import com.github.app.dify.appsystemdata.service.DataSourceService;
import com.github.app.dify.appsystemdata.service.DatabaseSchemaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * 数据源控制器
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private DatabaseSchemaService schemaService;
    
    /**
     * 创建数据源
     */
    @Operation(summary = "创建数据源")
    @PostMapping
    public ResponseEntity<?> createDataSource(
            @Validated @RequestBody CreateDataSourceReq req,
            @RequestParam(required = false, defaultValue = "false") Boolean force,
            HttpServletRequest request) {
        logger.info("接收到创建数据源请求 - 名称: {}, 类型: {}, 强制创建: {}", req.getName(), req.getType(), force);
        try {
            Long userId = (Long) request.getAttribute("userId");
            String username = (String) request.getAttribute("username");
            Integer role = (Integer) request.getAttribute("role");
            
            DataSourceResp resp = dataSourceService.createDataSource(req, userId, username, role, force);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("创建数据源失败", e);
            if (e.getMessage() != null && e.getMessage().startsWith("DUPLICATE_NAME:")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage().substring("DUPLICATE_NAME:".length()));
                errorResponse.put("code", "DUPLICATE_NAME");
                return ResponseEntity.status(409).body(errorResponse);
            }
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "创建数据源失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 更新数据源
     */
    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    public ResponseEntity<DataSourceResp> updateDataSource(
            @PathVariable Long id,
            @Validated @RequestBody UpdateDataSourceReq req) {
        try {
            DataSourceResp resp = dataSourceService.updateDataSource(id, req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("更新数据源失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据ID获取数据源
     */
    @Operation(summary = "根据ID获取数据源")
    @GetMapping("/{id}")
    public ResponseEntity<DataSourceResp> getDataSourceById(@PathVariable Long id) {
        try {
            DataSourceResp resp = dataSourceService.getDataSourceById(id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取数据源失败", e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除数据源
     */
    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable Long id) {
        try {
            dataSourceService.deleteDataSource(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除数据源失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取数据源列表
     */
    @Operation(summary = "获取数据源列表")
    @GetMapping
    public ResponseEntity<?> listDataSources(
            @RequestParam(required = false) Integer tenantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long userId,
            HttpServletRequest request) {
        try {
            Long currentUserId = (Long) request.getAttribute("userId");
            Integer userRole = (Integer) request.getAttribute("role");
            
            if (userId == null && currentUserId != null) {
                userId = currentUserId;
            }
            
            List<DataSourceResp> resp = dataSourceService.listDataSources(
                    tenantId, status, keyword, type, userId, userRole);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取数据源列表失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "获取数据源列表失败");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 测试数据源连接
     */
    @Operation(summary = "测试数据源连接")
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = dataSourceService.testConnection(id);
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("测试连接失败", e);
            result.put("success", false);
            result.put("message", "测试连接失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 测试数据源连接配置（用于创建/编辑时测试，不需要ID）
     */
    @Operation(summary = "测试数据源连接配置")
    @PostMapping("/test-config")
    public ResponseEntity<Map<String, Object>> testConnectionConfig(@RequestBody Map<String, Object> requestMap) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 从请求中提取字段，不进行严格验证（测试连接不需要name等字段）
            CreateDataSourceReq req = new CreateDataSourceReq();
            if (requestMap.containsKey("type")) {
                req.setType((String) requestMap.get("type"));
            }
            if (requestMap.containsKey("host")) {
                req.setHost((String) requestMap.get("host"));
            }
            if (requestMap.containsKey("port")) {
                Object portObj = requestMap.get("port");
                if (portObj != null) {
                    if (portObj instanceof Integer) {
                        req.setPort((Integer) portObj);
                    } else if (portObj instanceof Number) {
                        req.setPort(((Number) portObj).intValue());
                    }
                }
            }
            if (requestMap.containsKey("database")) {
                req.setDatabase((String) requestMap.get("database"));
            }
            if (requestMap.containsKey("username")) {
                req.setUsername((String) requestMap.get("username"));
            }
            if (requestMap.containsKey("password")) {
                req.setPassword((String) requestMap.get("password"));
            }
            
            // 验证必要字段
            if (req.getType() == null || req.getType().trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "数据库类型不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            if (req.getHost() == null || req.getHost().trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "主机地址不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            if (req.getPort() == null) {
                result.put("success", false);
                result.put("message", "端口不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            boolean success = dataSourceService.testConnectionConfig(req);
            result.put("success", success);
            result.put("message", success ? "连接成功" : "连接失败");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("测试连接配置失败", e);
            result.put("success", false);
            result.put("message", "测试连接失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    /**
     * 刷新表结构
     */
    @Operation(summary = "刷新表结构")
    @PostMapping("/{id}/refresh-schema")
    public ResponseEntity<Map<String, Object>> refreshSchema(
            @PathVariable Long id,
            @RequestParam(required = false) String tableName) {
        Map<String, Object> result = new HashMap<>();
        try {
            com.github.app.dify.appsystemdata.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(id);
            schemaService.refreshSchema(dataSource, tableName);
            result.put("success", true);
            result.put("message", "刷新成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("刷新表结构失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }
}