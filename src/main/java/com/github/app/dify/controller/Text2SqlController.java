package com.github.app.dify.controller;

import com.github.app.dify.service.DataSourceService;
import com.github.app.dify.service.DatabaseSchemaService;
import com.github.app.dify.service.Text2SqlService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Text2SQL 控制器
 */
@Tag(name = "Text2SQL")
@RestController
@RequestMapping("/api/text2sql")
public class Text2SqlController {
    
    private static final Logger logger = LoggerFactory.getLogger(Text2SqlController.class);
    
    @Autowired
    private Text2SqlService text2SqlService;
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private DatabaseSchemaService schemaService;
    
    /**
     * 执行 Text2SQL 查询
     */
    @Operation(summary = "执行 Text2SQL 查询")
    @PostMapping("/query")
    public ResponseEntity<?> executeQuery(@RequestBody Map<String, Object> request) {
        try {
            Long dataSourceId = Long.valueOf(request.get("dataSourceId").toString());
            String question = (String) request.get("question");
            Long modelId = request.get("modelId") != null ? Long.valueOf(request.get("modelId").toString()) : null;
            @SuppressWarnings("unchecked")
            List<String> tableNames = (List<String>) request.get("tableNames");
            
            if (dataSourceId == null || question == null || question.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "数据源ID和问题不能为空");
                return ResponseEntity.badRequest().body(error);
            }
            
            Text2SqlService.Text2SqlResult result = text2SqlService.executeQuery(
                    dataSourceId, question, modelId, tableNames);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("执行 Text2SQL 查询失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : "执行查询失败");
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 获取表列表
     */
    @Operation(summary = "获取表列表")
    @GetMapping("/{dataSourceId}/tables")
    public ResponseEntity<?> getTableList(@PathVariable Long dataSourceId) {
        try {
            com.github.app.dify.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
            List<String> tables = schemaService.getTableList(dataSource);
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            logger.error("获取表列表失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : "获取表列表失败");
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 获取表结构
     */
    @Operation(summary = "获取表结构")
    @GetMapping("/{dataSourceId}/tables/{tableName}/schema")
    public ResponseEntity<?> getTableSchema(
            @PathVariable Long dataSourceId,
            @PathVariable String tableName,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        try {
            com.github.app.dify.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
            String schema = schemaService.getTableSchema(dataSource, tableName, forceRefresh);
            return ResponseEntity.ok(schema);
        } catch (Exception e) {
            logger.error("获取表结构失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage() != null ? e.getMessage() : "获取表结构失败");
            return ResponseEntity.badRequest().body(error);
        }
    }
}