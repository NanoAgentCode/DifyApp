package com.github.app.dify.datasource.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.util.ServiceHelper;
import com.github.app.dify.datasource.req.CreateDataSourceReq;
import com.github.app.dify.datasource.req.UpdateDataSourceReq;
import com.github.app.dify.datasource.resp.DataSourceResp;
import com.github.app.dify.datasource.service.DataSourceService;
import com.github.app.dify.datasource.service.DatabaseSchemaService;
import com.github.app.dify.ops.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
public class DataSourceController extends BaseController {
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private DatabaseSchemaService schemaService;
    
    /**
     * 创建数据源
     */
    @UserAction(module = "数据源管理", actionType = "创建", description = "创建数据源")
    @Operation(summary = "创建数据源")
    @PostMapping
    public ResponseEntity<DataSourceResp> createDataSource(
            @Validated @RequestBody CreateDataSourceReq req,
            @RequestParam(required = false, defaultValue = "false") Boolean force,
            HttpServletRequest request) {
        logger.info("接收到创建数据源请求 - 名称: {}, 类型: {}, 强制创建: {}", req.getName(), req.getType(), force);
        
        Long userId = getUserId(request);
        String username = getUsername(request);
        Integer role = getRole(request);
        
        try {
            DataSourceResp resp = dataSourceService.createDataSource(req, userId, username, role, force);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            // 如果是重复名称错误，返回409 Conflict
            if (e.getMessage() != null && e.getMessage().startsWith("DUPLICATE_NAME:")) {
                String errorMsg = e.getMessage().substring("DUPLICATE_NAME:".length());
                throw new BusinessException(errorMsg, 409);
            }
            throw new BusinessException(e.getMessage() != null ? e.getMessage() : "创建数据源失败");
        }
    }
    
    /**
     * 更新数据源
     */
    @UserAction(module = "数据源管理", actionType = "更新", description = "更新数据源配置")
    @Operation(summary = "更新数据源")
    @PutMapping("/{id}")
    public ResponseEntity<DataSourceResp> updateDataSource(
            @PathVariable Long id,
            @Validated @RequestBody UpdateDataSourceReq req) {
        DataSourceResp resp = ServiceHelper.checkNotNull(
                dataSourceService.updateDataSource(id, req), "数据源", id, logger);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据ID获取数据源
     */
    @Operation(summary = "根据ID获取数据源")
    @GetMapping("/{id}")
    public ResponseEntity<DataSourceResp> getDataSourceById(@PathVariable Long id) {
        DataSourceResp resp = ServiceHelper.checkNotNull(
                dataSourceService.getDataSourceById(id), "数据源", id, logger);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除数据源
     */
    @UserAction(module = "数据源管理", actionType = "删除", description = "删除数据源")
    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable Long id) {
        dataSourceService.deleteDataSource(id);
        return ResponseEntity.ok().build();
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
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            HttpServletRequest request) {
        Long currentUserId = getUserId(request);
        Integer userRole = getRole(request);
        
        if (userId == null) {
            userId = currentUserId;
        }
        
        // 如果指定了分页参数，使用分页接口
        if (page != null && pageSize != null && page > 0 && pageSize > 0) {
            com.github.app.dify.common.resp.PageResponse<DataSourceResp> pageResponse = 
                    dataSourceService.listDataSourcesWithPagination(
                            tenantId, status, keyword, type, userId, userRole, page, pageSize);
            return ResponseEntity.ok(pageResponse);
        } else {
            // 否则返回所有数据（兼容旧接口）
            List<DataSourceResp> resp = dataSourceService.listDataSources(
                    tenantId, status, keyword, type, userId, userRole);
            return ResponseEntity.ok(resp);
        }
    }
    
    /**
     * 测试数据源连接
     */
    @UserAction(module = "数据源管理", actionType = "测试连接", description = "测试数据源连接")
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
    @UserAction(module = "数据源管理", actionType = "刷新表结构", description = "刷新数据源表结构")
    @Operation(summary = "刷新表结构")
    @PostMapping("/{id}/refresh-schema")
    public ResponseEntity<Map<String, Object>> refreshSchema(
            @PathVariable Long id,
            @RequestParam(required = false) String tableName) {
        Map<String, Object> result = new HashMap<>();
        try {
            com.github.app.dify.datasource.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(id);
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