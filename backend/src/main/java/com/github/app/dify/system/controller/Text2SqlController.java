package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.system.service.DataSourceService;
import com.github.app.dify.system.service.DatabaseSchemaService;
import com.github.app.dify.system.service.Text2SqlService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * Text2SQL 控制器
 */
@Tag(name = "Text2SQL")
@RestController
@RequestMapping("/api/text2sql")
public class Text2SqlController extends BaseController {
    
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
    public ResponseEntity<Text2SqlService.Text2SqlResult> executeQuery(@RequestBody Map<String, Object> request) {
        Long dataSourceId = Long.valueOf(request.get("dataSourceId").toString());
        String question = (String) request.get("question");
        Long modelId = request.get("modelId") != null ? Long.valueOf(request.get("modelId").toString()) : null;
        @SuppressWarnings("unchecked")
        List<String> tableNames = (List<String>) request.get("tableNames");
        
        if (dataSourceId == null || question == null || question.trim().isEmpty()) {
            throw new BusinessException("数据源ID和问题不能为空");
        }
        
        Text2SqlService.Text2SqlResult result = text2SqlService.executeQuery(
                dataSourceId, question, modelId, tableNames);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取表列表
     */
    @Operation(summary = "获取表列表")
    @GetMapping("/{dataSourceId}/tables")
    public ResponseEntity<List<String>> getTableList(@PathVariable Long dataSourceId) {
        com.github.app.dify.system.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
        List<String> tables = schemaService.getTableList(dataSource);
        return ResponseEntity.ok(tables);
    }
    
    /**
     * 获取表结构
     */
    @Operation(summary = "获取表结构")
    @GetMapping("/{dataSourceId}/tables/{tableName}/schema")
    public ResponseEntity<String> getTableSchema(
            @PathVariable Long dataSourceId,
            @PathVariable String tableName,
            @RequestParam(required = false, defaultValue = "false") Boolean forceRefresh) {
        com.github.app.dify.system.domain.DataSource dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
        String schema = schemaService.getTableSchema(dataSource, tableName, forceRefresh);
        return ResponseEntity.ok(schema);
    }
}