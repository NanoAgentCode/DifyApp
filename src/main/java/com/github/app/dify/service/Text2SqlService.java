package com.github.app.dify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.domain.DataSource;
import com.github.app.dify.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.util.DatabaseDriverManager;
import com.mongodb.client.MongoDatabase;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

/**
 * Text2SQL 服务
 * 使用 LangChain4j 实现自然语言到 SQL 的转换
 */
@Service
public class Text2SqlService {
    
    private static final Logger logger = LoggerFactory.getLogger(Text2SqlService.class);
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private DatabaseSchemaService schemaService;
    
    @Autowired
    private DatabaseConnectionService connectionService;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private com.github.app.dify.service.ModelConfigService modelConfigService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 执行 Text2SQL 查询
     * @param dataSourceId 数据源ID
     * @param question 用户问题
     * @param modelId 模型ID（可选，如果不指定则使用默认模型）
     * @param tableNames 表名列表（可选，如果指定则只使用这些表的结构）
     * @return 查询结果
     */
    public Text2SqlResult executeQuery(Long dataSourceId, String question, Long modelId, List<String> tableNames) {
        if (dataSourceId == null || question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源ID和问题不能为空");
        }
        
        // 获取数据源
        DataSource dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
        if (dataSource.getStatus() == null || dataSource.getStatus() != 1) {
            throw new RuntimeException("数据源未启用");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        
        try {
            // 获取表结构信息
            String schemaInfo = getSchemaInfo(dataSource, tableNames);
            
            // 生成 SQL
            String sql = generateSql(dataSource, question, schemaInfo, modelId);
            
            logger.info("生成的SQL - 数据源ID: {}, SQL: {}", dataSourceId, sql);
            
            // 执行 SQL
            QueryResult result = executeSql(dataSource, dbType, sql);
            
            Text2SqlResult text2SqlResult = new Text2SqlResult();
            text2SqlResult.setSql(sql);
            text2SqlResult.setColumns(result.getColumns());
            text2SqlResult.setRows(result.getRows());
            text2SqlResult.setRowCount(result.getRowCount());
            
            return text2SqlResult;
        } catch (Exception e) {
            logger.error("执行 Text2SQL 查询失败 - 数据源ID: {}, 问题: {}", dataSourceId, question, e);
            throw new RuntimeException("执行查询失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取表结构信息（包含表关联关系）
     */
    private String getSchemaInfo(DataSource dataSource, List<String> tableNames) {
        try {
            List<String> targetTables;
            if (tableNames != null && !tableNames.isEmpty()) {
                targetTables = tableNames;
            } else {
                targetTables = schemaService.getTableList(dataSource);
            }
            
            // 获取所有表的结构
            List<Map<String, Object>> tableSchemas = new ArrayList<>();
            Map<String, List<Map<String, Object>>> tableRelations = new HashMap<>();
            
            for (String tableName : targetTables) {
                String schemaJson = schemaService.getTableSchema(dataSource, tableName, false);
                Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);
                tableSchemas.add(schema);
                
                // 提取外键关联关系
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> foreignKeys = (List<Map<String, Object>>) schema.get("foreignKeys");
                if (foreignKeys != null && !foreignKeys.isEmpty()) {
                    tableRelations.put(tableName, foreignKeys);
                }
            }
            
            // 构建包含关联关系的schema信息
            StringBuilder schemaBuilder = new StringBuilder();
            schemaBuilder.append("表结构信息：\n");
            for (Map<String, Object> schema : tableSchemas) {
                schemaBuilder.append(objectMapper.writeValueAsString(schema)).append("\n");
            }
            
            // 添加表关联关系说明
            if (!tableRelations.isEmpty()) {
                schemaBuilder.append("\n表关联关系：\n");
                for (Map.Entry<String, List<Map<String, Object>>> entry : tableRelations.entrySet()) {
                    String tableName = entry.getKey();
                    List<Map<String, Object>> fks = entry.getValue();
                    for (Map<String, Object> fk : fks) {
                        schemaBuilder.append(String.format("- %s.%s -> %s.%s\n",
                            tableName,
                            fk.get("columnName"),
                            fk.get("pkTableName"),
                            fk.get("pkColumnName")));
                    }
                }
            }
            
            return schemaBuilder.toString();
        } catch (Exception e) {
            logger.error("获取表结构信息失败", e);
            throw new RuntimeException("获取表结构信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成 SQL
     */
    private String generateSql(DataSource dataSource, String question, String schemaInfo, Long modelId) {
        // 构建提示词
        String prompt = buildPrompt(dataSource, question, schemaInfo);
        
        // 获取问答模型
        com.github.app.dify.domain.QAModel qaModel;
        try {
            if (modelId != null) {
                // 使用指定的模型
                qaModel = modelConfigService.getQAModelById(modelId);
            } else {
                // 使用默认的RAG模型
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
        } catch (Exception e) {
            logger.error("获取问答模型失败，使用默认模型 - modelId: {}", modelId, e);
            qaModel = modelConfigService.getDefaultQAModelForRAG();
        }
        
        // 创建 LLM 模型
        ModelLanguageModelFactory.ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
        
        // 构建系统消息
        SystemMessage systemMessage = new SystemMessage(buildSystemPrompt(dataSource.getType()));
        
        // 构建用户消息
        UserMessage userMessage = new UserMessage(prompt);
        
        // 调用 LLM 生成 SQL
        Response<dev.langchain4j.data.message.AiMessage> response = chatModel.generate(
                Arrays.asList(systemMessage, userMessage));
        
        String sql = response.content().text();
        
        // 清理 SQL（移除可能的代码块标记）
        sql = cleanSql(sql);
        
        // SQL 安全检查
        validateSql(sql);
        
        // 验证SQL中使用的列是否存在于表结构中
        validateSqlColumns(sql, schemaInfo);
        
        // PostgreSQL 类型转换修复
        if ("postgresql".equalsIgnoreCase(dataSource.getType())) {
            sql = fixPostgreSqlTypeCasting(sql, schemaInfo);
        }
        
        return sql;
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String databaseType) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的SQL生成助手。根据用户的问题和数据库表结构，生成正确的SQL查询语句。\n");
        sb.append("数据库类型: ").append(databaseType).append("\n");
        sb.append("要求：\n");
        sb.append("1. 只生成SELECT查询语句，不要生成INSERT、UPDATE、DELETE、DROP等修改数据的语句\n");
        sb.append("2. SQL语句必须符合").append(databaseType).append("的语法规范\n");
        sb.append("3. 只返回SQL语句，不要包含其他解释性文字\n");
        sb.append("4. 如果问题无法转换为SQL，返回错误信息\n");
        sb.append("5. 重要：只能使用表结构信息中提供的列名，不要使用未提供的列（如tenant_id、deleted、create_time、update_time等系统列）\n");
        sb.append("6. 如果表结构信息中没有某个列，说明该列不存在或不可用，绝对不要使用它\n");
        sb.append("7. 支持统计查询：可以使用COUNT、SUM、AVG、MAX、MIN等聚合函数进行统计分析\n");
        sb.append("8. 统计函数示例：COUNT(*)统计记录数，SUM(column)求和，AVG(column)求平均值，MAX(column)求最大值，MIN(column)求最小值\n");
        sb.append("9. 支持多表关联查询：当问题涉及多个表时，必须使用JOIN进行关联\n");
        sb.append("10. JOIN语法：\n");
        sb.append("    - INNER JOIN：内连接，只返回两表都匹配的记录\n");
        sb.append("    - LEFT JOIN：左连接，返回左表所有记录和右表匹配的记录\n");
        sb.append("    - RIGHT JOIN：右连接，返回右表所有记录和左表匹配的记录\n");
        sb.append("    - 关联条件：使用ON关键字，例如：ON table1.column = table2.column\n");
        sb.append("11. 多表关联示例：\n");
        sb.append("    SELECT t1.col1, t2.col2 FROM table1 t1 INNER JOIN table2 t2 ON t1.id = t2.table1_id\n");
        sb.append("12. 当查询涉及多个表时，必须根据表关联关系正确使用JOIN，关联关系信息会在表结构信息中提供\n");
        sb.append("13. 如果表结构信息中提供了外键关联关系（格式：table1.column -> table2.column），请使用这些关系进行JOIN\n");
        sb.append("14. 多表查询时，列名必须使用表别名或表名作为前缀，避免列名冲突，例如：table1.name 或 t1.name\n");
        
        // PostgreSQL 特殊要求
        if ("postgresql".equalsIgnoreCase(databaseType)) {
            sb.append("\n");
            sb.append("PostgreSQL 特殊要求：\n");
            sb.append("- 当比较不同数据类型的字段时，必须使用显式类型转换（CAST）\n");
            sb.append("- 例如：比较 bigint 和 varchar 时，使用 CAST(column AS bigint) 或 column::bigint\n");
            sb.append("- 字符串比较时，确保类型一致，必要时使用 CAST(value AS varchar) 或 value::varchar\n");
            sb.append("- WHERE 条件中的参数值必须与列的数据类型匹配，不匹配时使用类型转换\n");
            sb.append("- 示例：WHERE id = CAST('123' AS bigint) 或 WHERE id = '123'::bigint\n");
        }
        
        // MySQL 特殊要求
        if ("mysql".equalsIgnoreCase(databaseType)) {
            sb.append("\n");
            sb.append("MySQL 特殊要求：\n");
            sb.append("- 字符串使用单引号，不要使用双引号\n");
            sb.append("- 日期时间格式使用 'YYYY-MM-DD HH:MM:SS' 格式\n");
        }
        
        // Oracle 特殊要求
        if ("oracle".equalsIgnoreCase(databaseType)) {
            sb.append("\n");
            sb.append("Oracle 特殊要求：\n");
            sb.append("- 字符串使用单引号\n");
            sb.append("- 日期时间使用 TO_DATE 函数\n");
            sb.append("- 注意大小写敏感性\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 构建用户提示词
     */
    private String buildPrompt(DataSource dataSource, String question, String schemaInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据库信息：\n");
        sb.append("数据库类型: ").append(dataSource.getType()).append("\n");
        sb.append("数据库名称: ").append(dataSource.getDatabase()).append("\n");
        sb.append("\n");
        sb.append("表结构信息（JSON格式，包含列名和数据类型）：\n");
        sb.append(schemaInfo);
        sb.append("\n");
        
        // 针对 PostgreSQL 添加特殊提示
        if ("postgresql".equalsIgnoreCase(dataSource.getType())) {
            sb.append("\n");
            sb.append("重要提示（PostgreSQL）：\n");
            sb.append("- 在 WHERE 条件中比较字段时，如果数据类型不匹配，必须使用类型转换\n");
            sb.append("- 例如：如果 id 是 bigint 类型，比较时使用 WHERE id = CAST('123' AS bigint)\n");
            sb.append("- 或者使用简写形式：WHERE id = '123'::bigint\n");
            sb.append("- 字符串值必须与列的数据类型匹配，必要时进行类型转换\n");
        }
        
        sb.append("\n");
        sb.append("重要提示：\n");
        sb.append("- 只能使用上面表结构信息中列出的列名\n");
        sb.append("- 不要使用表结构信息中未提供的列（如tenant_id、deleted、create_time、update_time等系统列）\n");
        sb.append("- 如果表结构信息中没有某个列，说明该列不存在，绝对不要使用它\n");
        sb.append("\n");
        sb.append("多表关联查询说明：\n");
        sb.append("- 当问题涉及多个表的数据时，必须使用JOIN进行表关联\n");
        sb.append("- 表关联关系信息在表结构信息中提供，格式为：table1.column -> table2.column\n");
        sb.append("- 使用JOIN时，必须正确设置ON条件，例如：ON table1.foreign_key = table2.primary_key\n");
        sb.append("- 多表查询时，列名必须使用表别名或表名作为前缀，例如：table1.name 或 t1.name\n");
        sb.append("- 推荐使用表别名（AS关键字或空格）简化SQL，例如：FROM users u INNER JOIN orders o ON u.id = o.user_id\n");
        sb.append("- 支持多个表的关联，例如：table1 JOIN table2 ON ... JOIN table3 ON ...\n");
        sb.append("- 如果问题需要关联多个表，请使用INNER JOIN、LEFT JOIN或RIGHT JOIN，根据业务逻辑选择合适的JOIN类型\n");
        sb.append("\n");
        sb.append("统计查询支持：\n");
        sb.append("- COUNT(*) 或 COUNT(column)：统计记录数或非空值数量\n");
        sb.append("- SUM(column)：对数值列求和\n");
        sb.append("- AVG(column)：计算数值列的平均值\n");
        sb.append("- MAX(column)：获取最大值\n");
        sb.append("- MIN(column)：获取最小值\n");
        sb.append("- 可以使用 GROUP BY 进行分组统计\n");
        sb.append("- 可以使用 HAVING 对分组结果进行过滤\n");
        sb.append("- 多表关联时，聚合函数可以使用表别名，例如：COUNT(t1.id)、SUM(t2.amount)\n");
        sb.append("\n");
        sb.append("用户问题：\n");
        sb.append(question);
        sb.append("\n");
        sb.append("请根据以上信息生成SQL查询语句（注意数据类型匹配，只使用表结构信息中提供的列，支持多表关联查询和统计查询）：");
        return sb.toString();
    }
    
    /**
     * 清理 SQL（移除代码块标记等）
     */
    private String cleanSql(String sql) {
        if (sql == null) {
            return null;
        }
        
        sql = sql.trim();
        
        // 移除可能的代码块标记
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        }
        if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        
        return sql.trim();
    }
    
    /**
     * 修复 PostgreSQL 类型转换问题
     * 自动检测 WHERE 条件中的类型不匹配并添加 CAST
     */
    private String fixPostgreSqlTypeCasting(String sql, String schemaInfo) {
        try {
            // 解析表结构信息，构建列名到类型的映射
            Map<String, String> columnTypeMap = parseColumnTypes(schemaInfo);
            
            if (columnTypeMap.isEmpty()) {
                logger.warn("无法解析表结构信息，跳过类型转换修复");
                return sql;
            }
            
            // 修复 WHERE 条件中的类型转换
            String fixedSql = fixWhereClauseTypeCasting(sql, columnTypeMap);
            
            if (!fixedSql.equals(sql)) {
                logger.info("已自动修复PostgreSQL类型转换 - 原始SQL: {}, 修复后SQL: {}", sql, fixedSql);
            }
            
            return fixedSql;
        } catch (Exception e) {
            logger.warn("修复PostgreSQL类型转换时出错，返回原始SQL", e);
            return sql;
        }
    }
    
    /**
     * 解析表结构信息，构建列名到类型的映射
     */
    private Map<String, String> parseColumnTypes(String schemaInfo) {
        Map<String, String> columnTypeMap = new HashMap<>();
        
        try {
            // schemaInfo 可能是多个表的JSON，每行一个JSON对象
            String[] lines = schemaInfo.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("{")) {
                    continue;
                }
                
                try {
                    // 解析JSON
                    Map<String, Object> schema = objectMapper.readValue(line, Map.class);
                    
                    // 获取表名
                    String tableName = (String) schema.get("tableName");
                    if (tableName == null) {
                        continue;
                    }
                    
                    // 获取列信息
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) schema.get("columns");
                    if (columns != null) {
                        for (Map<String, Object> column : columns) {
                            String columnName = (String) column.get("name");
                            String columnType = (String) column.get("type");
                            
                            if (columnName != null && columnType != null) {
                                // 使用 "表名.列名" 作为key，以支持多表查询
                                String key = tableName.toLowerCase() + "." + columnName.toLowerCase();
                                columnTypeMap.put(key, columnType.toLowerCase());
                                
                                // 也支持只有列名的情况（用于单表查询）
                                columnTypeMap.put(columnName.toLowerCase(), columnType.toLowerCase());
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析失败的JSON行
                    logger.debug("解析表结构JSON失败: {}", line, e);
                }
            }
        } catch (Exception e) {
            logger.error("解析表结构信息失败", e);
        }
        
        return columnTypeMap;
    }
    
    /**
     * 修复 WHERE 子句中的类型转换问题
     */
    private String fixWhereClauseTypeCasting(String sql, Map<String, String> columnTypeMap) {
        if (columnTypeMap.isEmpty()) {
            return sql;
        }
        
        // 如果SQL中已经包含CAST，说明可能已经处理过，跳过
        if (sql.toUpperCase().contains("CAST(")) {
            logger.debug("SQL中已包含CAST，跳过自动修复");
            return sql;
        }
        
        // 匹配 WHERE 条件中的比较表达式
        // 匹配模式：列名 = '值' 或 列名 = "值"
        // 支持 =, !=, <>, <, >, <=, >=, LIKE 等操作符（IN需要特殊处理）
        
        String fixedSql = sql;
        
        // 匹配常见的比较模式：column = 'value' 或 column = "value"
        // 使用正则表达式匹配，考虑表名前缀（table.column）和引号
        // 排除已经包含CAST的情况
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s*(=|[<>!]=|<>|<=|>=|<|>|\\s+LIKE\\s+)\\s*('([^']*)'|\"([^\"]*)\")",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(fixedSql);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            // 检查匹配位置前后是否已经有CAST，如果有则跳过
            int start = matcher.start();
            int end = matcher.end();
            String beforeMatch = fixedSql.substring(Math.max(0, start - 50), start);
            if (beforeMatch.toUpperCase().contains("CAST(")) {
                matcher.appendReplacement(sb, matcher.group(0));
                continue;
            }
            
            String columnRef = matcher.group(1); // 列名（可能包含表名）
            String operator = matcher.group(2).trim();  // 操作符
            String value = matcher.group(4) != null ? matcher.group(4) : matcher.group(5); // 值
            
            String originalMatch = matcher.group(0);
            String replacement = originalMatch;
            
            // 查找列类型
            String columnKey = columnRef.toLowerCase();
            String columnType = columnTypeMap.get(columnKey);
            
            // 如果找不到，尝试只使用列名部分（去掉表名前缀）
            if (columnType == null && columnRef.contains(".")) {
                String columnName = columnRef.substring(columnRef.lastIndexOf('.') + 1);
                columnType = columnTypeMap.get(columnName.toLowerCase());
            }
            
            // 如果找到了列类型，且值是字符串，但列类型是数字类型，需要添加CAST
            if (columnType != null && value != null && !value.isEmpty()) {
                // 检查是否是数字类型
                boolean isNumericType = columnType.contains("int") || 
                                       columnType.contains("bigint") ||
                                       columnType.contains("smallint") ||
                                       columnType.contains("numeric") ||
                                       columnType.contains("decimal") ||
                                       columnType.contains("float") ||
                                       columnType.contains("double") ||
                                       columnType.contains("real") ||
                                       columnType.contains("serial");
                
                // 如果列是数字类型，但值是字符串，需要添加CAST
                if (isNumericType) {
                    // 确定PostgreSQL类型名称
                    String pgType = mapToPostgreSqlType(columnType);
                    
                    // 构建替换：将 'value' 替换为 CAST('value' AS pgType)
                    String quotedValue = matcher.group(4) != null ? "'" + value + "'" : "\"" + value + "\"";
                    replacement = columnRef + " " + operator + " CAST(" + quotedValue + " AS " + pgType + ")";
                    
                    logger.debug("修复类型转换: {} -> {}", originalMatch, replacement);
                }
            }
            
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        
        matcher.appendTail(sb);
        fixedSql = sb.toString();
        
        // 处理 IN 操作符的情况：column IN ('value1', 'value2', ...)
        fixedSql = fixInClauseTypeCasting(fixedSql, columnTypeMap);
        
        return fixedSql;
    }
    
    /**
     * 修复 IN 子句中的类型转换问题
     */
    private String fixInClauseTypeCasting(String sql, Map<String, String> columnTypeMap) {
        // 匹配 IN 子句：column IN ('value1', 'value2', ...)
        java.util.regex.Pattern inPattern = java.util.regex.Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\s+IN\\s*\\(([^)]+)\\)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher inMatcher = inPattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        
        while (inMatcher.find()) {
            String columnRef = inMatcher.group(1);
            String valuesStr = inMatcher.group(2);
            
            // 查找列类型
            String columnKey = columnRef.toLowerCase();
            String columnType = columnTypeMap.get(columnKey);
            
            if (columnType == null && columnRef.contains(".")) {
                String columnName = columnRef.substring(columnRef.lastIndexOf('.') + 1);
                columnType = columnTypeMap.get(columnName.toLowerCase());
            }
            
            if (columnType != null) {
                boolean isNumericType = columnType.contains("int") || 
                                       columnType.contains("bigint") ||
                                       columnType.contains("smallint") ||
                                       columnType.contains("numeric") ||
                                       columnType.contains("decimal") ||
                                       columnType.contains("float") ||
                                       columnType.contains("double") ||
                                       columnType.contains("real") ||
                                       columnType.contains("serial");
                
                if (isNumericType) {
                    // 检查值列表中是否有字符串值
                    java.util.regex.Pattern valuePattern = java.util.regex.Pattern.compile("('([^']*)'|\"([^\"]*)\")");
                    java.util.regex.Matcher valueMatcher = valuePattern.matcher(valuesStr);
                    
                    if (valueMatcher.find()) {
                        // 有字符串值，需要转换
                        String pgType = mapToPostgreSqlType(columnType);
                        String fixedValues = valuesStr.replaceAll("('([^']*)'|\"([^\"]*)\")", 
                            "CAST($1 AS " + pgType + ")");
                        
                        String replacement = columnRef + " IN (" + fixedValues + ")";
                        inMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
                        logger.debug("修复IN子句类型转换: {} IN ({}) -> {}", columnRef, valuesStr, replacement);
                        continue;
                    }
                }
            }
            
            inMatcher.appendReplacement(sb, inMatcher.group(0));
        }
        
        inMatcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * 将JDBC类型名称映射到PostgreSQL类型名称
     */
    private String mapToPostgreSqlType(String jdbcType) {
        String lowerType = jdbcType.toLowerCase();
        
        if (lowerType.contains("bigint")) {
            return "bigint";
        } else if (lowerType.contains("int") && !lowerType.contains("bigint") && !lowerType.contains("smallint")) {
            return "integer";
        } else if (lowerType.contains("smallint")) {
            return "smallint";
        } else if (lowerType.contains("numeric") || lowerType.contains("decimal")) {
            return "numeric";
        } else if (lowerType.contains("float") || lowerType.contains("double") || lowerType.contains("real")) {
            return "double precision";
        } else if (lowerType.contains("serial")) {
            return "bigint"; // serial 在比较时应该转换为 bigint
        } else {
            // 默认返回原类型
            return jdbcType;
        }
    }
    
    /**
     * 验证SQL中使用的列是否存在于表结构中
     */
    private void validateSqlColumns(String sql, String schemaInfo) {
        try {
            // 解析表结构信息，获取所有可用的列名
            Map<String, Set<String>> tableColumnsMap = parseTableColumns(schemaInfo);
            
            if (tableColumnsMap.isEmpty()) {
                logger.warn("无法解析表结构信息，跳过列验证");
                return;
            }
            
            // 从SQL中提取使用的列名
            Set<String> usedColumns = extractColumnsFromSql(sql);
            
            // 检查每个使用的列是否存在于表结构中
            List<String> invalidColumns = new ArrayList<>();
            for (String columnRef : usedColumns) {
                boolean found = false;
                
                // 检查是否包含表名前缀（如 table.column）
                if (columnRef.contains(".")) {
                    String[] parts = columnRef.split("\\.");
                    if (parts.length == 2) {
                        String tableName = parts[0].toLowerCase();
                        String columnName = parts[1].toLowerCase();
                        Set<String> columns = tableColumnsMap.get(tableName);
                        if (columns != null && columns.contains(columnName)) {
                            found = true;
                        }
                    }
                } else {
                    // 没有表名前缀，在所有表中查找
                    String columnName = columnRef.toLowerCase();
                    for (Set<String> columns : tableColumnsMap.values()) {
                        if (columns.contains(columnName)) {
                            found = true;
                            break;
                        }
                    }
                }
                
                if (!found) {
                    invalidColumns.add(columnRef);
                }
            }
            
            // 如果发现不存在的列，抛出错误
            if (!invalidColumns.isEmpty()) {
                String errorMsg = String.format(
                    "SQL中使用了不存在的列: %s。请只使用表结构信息中提供的列名。",
                    String.join(", ", invalidColumns)
                );
                logger.error("SQL列验证失败: {}", errorMsg);
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            // 重新抛出运行时异常
            throw e;
        } catch (Exception e) {
            // 其他异常只记录日志，不阻止执行
            logger.warn("验证SQL列时出错，继续执行", e);
        }
    }
    
    /**
     * 解析表结构信息，获取表名到列名的映射
     */
    private Map<String, Set<String>> parseTableColumns(String schemaInfo) {
        Map<String, Set<String>> tableColumnsMap = new HashMap<>();
        
        try {
            // schemaInfo 可能是多个表的JSON，每行一个JSON对象
            String[] lines = schemaInfo.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("{")) {
                    continue;
                }
                
                try {
                    // 解析JSON
                    Map<String, Object> schema = objectMapper.readValue(line, Map.class);
                    
                    // 获取表名
                    String tableName = (String) schema.get("tableName");
                    if (tableName == null) {
                        continue;
                    }
                    
                    // 获取列信息
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> columns = (List<Map<String, Object>>) schema.get("columns");
                    if (columns != null) {
                        Set<String> columnNames = new HashSet<>();
                        for (Map<String, Object> column : columns) {
                            String columnName = (String) column.get("name");
                            if (columnName != null) {
                                columnNames.add(columnName.toLowerCase());
                            }
                        }
                        tableColumnsMap.put(tableName.toLowerCase(), columnNames);
                    }
                } catch (Exception e) {
                    // 忽略解析失败的JSON行
                    logger.debug("解析表结构JSON失败: {}", line, e);
                }
            }
        } catch (Exception e) {
            logger.error("解析表结构信息失败", e);
        }
        
        return tableColumnsMap;
    }
    
    /**
     * 从SQL中提取使用的列名
     * 只提取真正的列名，排除表名、别名、函数名等
     */
    private Set<String> extractColumnsFromSql(String sql) {
        Set<String> columns = new HashSet<>();
        
        // SQL关键字列表（这些不是列名）
        Set<String> sqlKeywords = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "IS", "NULL",
            "ORDER", "BY", "GROUP", "HAVING", "AS", "COUNT", "SUM", "AVG", "MAX", "MIN",
            "DISTINCT", "CASE", "WHEN", "THEN", "ELSE", "END", "CAST", "JOIN", "INNER",
            "LEFT", "RIGHT", "FULL", "ON", "UNION", "ALL", "EXISTS", "BETWEEN", "IF",
            "CONCAT", "SUBSTRING", "UPPER", "LOWER", "TRIM", "LENGTH", "ROUND", "ABS",
            "TRUE", "FALSE"  // 布尔值字面量
        ));
        
        // 提取表名（FROM子句中的）
        Set<String> tableNames = extractTableNames(sql);
        
        // 提取别名（AS后面的）
        Set<String> aliases = extractAliases(sql);
        
        // 匹配列名模式：可能是 table.column 或 column
        // 重点匹配 WHERE、ORDER BY、GROUP BY、HAVING 子句中的列名
        String upperSql = sql.toUpperCase();
        
        // 1. 提取 WHERE 子句中的列名
        extractColumnsFromClause(sql, upperSql, "WHERE", sqlKeywords, tableNames, aliases, columns);
        
        // 2. 提取 ORDER BY 子句中的列名
        extractColumnsFromClause(sql, upperSql, "ORDER BY", sqlKeywords, tableNames, aliases, columns);
        
        // 3. 提取 GROUP BY 子句中的列名
        extractColumnsFromClause(sql, upperSql, "GROUP BY", sqlKeywords, tableNames, aliases, columns);
        
        // 4. 提取 HAVING 子句中的列名
        extractColumnsFromClause(sql, upperSql, "HAVING", sqlKeywords, tableNames, aliases, columns);
        
        // 5. 提取 SELECT 子句中的列名（排除函数和别名）
        extractColumnsFromSelect(sql, upperSql, sqlKeywords, tableNames, aliases, columns);
        
        return columns;
    }
    
    /**
     * 从特定子句（WHERE、ORDER BY等）中提取列名
     */
    private void extractColumnsFromClause(String sql, String upperSql, String clauseKeyword,
                                          Set<String> sqlKeywords, Set<String> tableNames,
                                          Set<String> aliases, Set<String> columns) {
        int clauseIndex = upperSql.indexOf(clauseKeyword);
        if (clauseIndex == -1) {
            return;
        }
        
        // 找到子句的结束位置（下一个关键字或SQL结束）
        int clauseStart = clauseIndex + clauseKeyword.length();
        int clauseEnd = sql.length();
        
        String[] nextKeywords = {"ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET"};
        for (String keyword : nextKeywords) {
            int nextIndex = upperSql.indexOf(keyword, clauseStart);
            if (nextIndex != -1 && nextIndex < clauseEnd) {
                clauseEnd = nextIndex;
            }
        }
        
        String clause = sql.substring(clauseStart, clauseEnd);
        extractColumnNamesFromText(clause, sqlKeywords, tableNames, aliases, columns);
    }
    
    /**
     * 从 SELECT 子句中提取列名（排除函数和别名）
     */
    private void extractColumnsFromSelect(String sql, String upperSql, Set<String> sqlKeywords,
                                         Set<String> tableNames, Set<String> aliases,
                                         Set<String> columns) {
        int selectIndex = upperSql.indexOf("SELECT");
        if (selectIndex == -1) {
            return;
        }
        
        int fromIndex = upperSql.indexOf("FROM", selectIndex);
        if (fromIndex == -1) {
            return;
        }
        
        String selectClause = sql.substring(selectIndex + 6, fromIndex);
        
        // 分割 SELECT 子句中的各个列（按逗号分割，但要考虑括号内的逗号）
        List<String> selectItems = splitSelectItems(selectClause);
        
        for (String item : selectItems) {
            item = item.trim();
            
            // 跳过 *
            if (item.equals("*")) {
                continue;
            }
            
            // 处理统计函数（COUNT、SUM、AVG、MAX、MIN等）
            String upperItem = item.toUpperCase();
            if (upperItem.startsWith("COUNT(") || upperItem.startsWith("SUM(") ||
                upperItem.startsWith("AVG(") || upperItem.startsWith("MAX(") || 
                upperItem.startsWith("MIN(") || upperItem.startsWith("STDDEV(") ||
                upperItem.startsWith("VARIANCE(") || upperItem.startsWith("STDDEV_POP(") ||
                upperItem.startsWith("STDDEV_SAMP(") || upperItem.startsWith("VAR_POP(") ||
                upperItem.startsWith("VAR_SAMP(")) {
                // 从统计函数中提取列名
                extractColumnFromAggregateFunction(item, sqlKeywords, tableNames, aliases, columns);
                continue;
            }
            
            // 处理 "column AS alias" 格式
            int asIndex = upperItem.indexOf(" AS ");
            if (asIndex != -1) {
                // 有别名，只提取 AS 前面的部分（列名）
                String beforeAs = item.substring(0, asIndex).trim();
                extractColumnNamesFromText(beforeAs, sqlKeywords, tableNames, aliases, columns);
            } else {
                // 没有别名，直接提取列名
                extractColumnNamesFromText(item, sqlKeywords, tableNames, aliases, columns);
            }
        }
    }
    
    /**
     * 从统计函数中提取列名（如SUM(column)、COUNT(column)等）
     */
    private void extractColumnFromAggregateFunction(String functionCall, Set<String> sqlKeywords,
                                                    Set<String> tableNames, Set<String> aliases,
                                                    Set<String> columns) {
        // 匹配函数调用：FUNCTION_NAME(...)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\w+\\s*\\(([^)]+)\\)",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(functionCall);
        if (matcher.find()) {
            String params = matcher.group(1).trim();
            
            // COUNT(*) 不需要验证列
            if (params.equals("*")) {
                return;
            }
            
            // 从函数参数中提取列名
            extractColumnNamesFromText(params, sqlKeywords, tableNames, aliases, columns);
        }
    }
    
    /**
     * 从文本中提取列名
     */
    private void extractColumnNamesFromText(String text, Set<String> sqlKeywords,
                                           Set<String> tableNames, Set<String> aliases,
                                           Set<String> columns) {
        // 匹配列名模式：可能是 table.column 或 column
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group(1);
            String lowerMatch = match.toLowerCase();
            String upperMatch = match.toUpperCase();
            
            // 跳过SQL关键字
            if (sqlKeywords.contains(upperMatch)) {
                continue;
            }
            
            // 跳过数字
            if (match.matches("^\\d+(\\.\\d+)?$")) {
                continue;
            }
            
            // 跳过布尔值字面量（TRUE、FALSE）
            if (upperMatch.equals("TRUE") || upperMatch.equals("FALSE")) {
                continue;
            }
            
            // 处理 table.column 格式
            if (match.contains(".")) {
                String[] parts = match.split("\\.");
                if (parts.length == 2) {
                    String tablePart = parts[0].toLowerCase();
                    String columnPart = parts[1].toLowerCase();
                    
                    // 如果表名部分在表名列表中，则只提取列名部分
                    if (tableNames.contains(tablePart)) {
                        // 跳过别名
                        if (!aliases.contains(columnPart)) {
                            columns.add(columnPart);
                        }
                        continue;
                    }
                }
            }
            
            // 跳过表名（单独的标识符，不是 table.column 格式）
            if (tableNames.contains(lowerMatch) && !match.contains(".")) {
                continue;
            }
            
            // 跳过别名
            if (aliases.contains(lowerMatch)) {
                continue;
            }
            
            // 检查是否在字符串值中
            int start = matcher.start();
            boolean inString = false;
            char quoteChar = 0;
            
            // 向前查找最近的引号
            for (int i = start - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '\'' || c == '"') {
                    if (quoteChar == 0) {
                        quoteChar = c;
                        inString = true;
                    } else if (c == quoteChar) {
                        if (i == 0 || text.charAt(i - 1) != '\\') {
                            inString = false;
                            break;
                        }
                    }
                }
            }
            
            // 如果不在字符串中，则可能是列名
            if (!inString) {
                columns.add(lowerMatch);
            }
        }
    }
    
    /**
     * 提取表名（FROM子句中的）
     */
    private Set<String> extractTableNames(String sql) {
        Set<String> tableNames = new HashSet<>();
        String upperSql = sql.toUpperCase();
        
        int fromIndex = upperSql.indexOf("FROM");
        if (fromIndex == -1) {
            return tableNames;
        }
        
        // 找到FROM后面的表名（直到WHERE、JOIN、ORDER BY等关键字）
        int tableStart = fromIndex + 4;
        int tableEnd = sql.length();
        
        String[] endKeywords = {"WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", 
                               "ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET"};
        for (String keyword : endKeywords) {
            int nextIndex = upperSql.indexOf(keyword, tableStart);
            if (nextIndex != -1 && nextIndex < tableEnd) {
                tableEnd = nextIndex;
            }
        }
        
        String fromClause = sql.substring(tableStart, tableEnd).trim();
        
        // 提取表名（可能包含别名，如 "table AS alias" 或 "table alias"）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(fromClause);
        if (matcher.find()) {
            String tableName = matcher.group(1).toLowerCase();
            tableNames.add(tableName);
        }
        
        return tableNames;
    }
    
    /**
     * 提取别名（AS后面的）
     */
    private Set<String> extractAliases(String sql) {
        Set<String> aliases = new HashSet<>();
        String upperSql = sql.toUpperCase();
        
        // 匹配 "AS alias" 模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\\bAS\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            String alias = matcher.group(1).toLowerCase();
            aliases.add(alias);
        }
        
        return aliases;
    }
    
    /**
     * 分割 SELECT 子句中的各个项（考虑括号）
     */
    private List<String> splitSelectItems(String selectClause) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        
        for (int i = 0; i < selectClause.length(); i++) {
            char c = selectClause.charAt(i);
            
            if (c == '(') {
                parenDepth++;
                current.append(c);
            } else if (c == ')') {
                parenDepth--;
                current.append(c);
            } else if (c == ',' && parenDepth == 0) {
                items.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            items.add(current.toString().trim());
        }
        
        return items;
    }
    
    /**
     * SQL 安全检查
     */
    private void validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new RuntimeException("SQL语句不能为空");
        }
        
        String upperSql = sql.toUpperCase().trim();
        
        // 只允许 SELECT 语句
        if (!upperSql.startsWith("SELECT")) {
            throw new RuntimeException("只允许执行SELECT查询语句");
        }
        
        // 使用正则表达式检测真正的 SQL 关键字（而不是字符串值中的关键字）
        // 匹配模式：关键字前后是空白字符、逗号、括号等 SQL 分隔符
        String[] dangerousKeywords = {
            "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "INSERT", 
            "UPDATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL"
        };
        
        for (String keyword : dangerousKeywords) {
            // 使用正则表达式匹配 SQL 关键字
            // 匹配模式：关键字前后是单词边界或 SQL 分隔符
            // 这样可以避免匹配字符串值中的关键字（如 WHERE name LIKE '%DELETE%'）
            String pattern = "\\b" + keyword + "\\b";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern, 
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = regex.matcher(sql);
            
            if (matcher.find()) {
                // 检查是否在字符串值中（单引号或双引号内）
                int start = matcher.start();
                int end = matcher.end();
                
                // 检查关键字前后是否有引号（可能在字符串值中）
                boolean inString = false;
                char quoteChar = 0;
                
                // 向前查找最近的引号
                for (int i = start - 1; i >= 0; i--) {
                    char c = sql.charAt(i);
                    if (c == '\'' || c == '"') {
                        if (quoteChar == 0) {
                            quoteChar = c;
                            inString = true;
                        } else if (c == quoteChar) {
                            // 找到匹配的引号，检查是否转义
                            if (i == 0 || sql.charAt(i - 1) != '\\') {
                                inString = false;
                                break;
                            }
                        }
                    }
                }
                
                // 如果不在字符串中，则是真正的 SQL 关键字，禁止执行
                if (!inString) {
                    throw new RuntimeException("禁止执行包含 " + keyword + " 的SQL语句");
                }
            }
        }
    }
    
    /**
     * 执行 SQL
     */
    private QueryResult executeSql(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType, String sql) {
        if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
            return executeMongoQuery(dataSource, sql);
        } else {
            return executeJdbcQuery(dataSource, sql);
        }
    }
    
    /**
     * 执行 JDBC 查询
     */
    private QueryResult executeJdbcQuery(DataSource dataSource, String sql) {
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try (Connection connection = connectionService.getConnection(dataSource);
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            // 获取列信息
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            
            // 获取数据
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                rows.add(row);
            }
            
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
        } catch (SQLException e) {
            logger.error("执行SQL查询失败 - SQL: {}", sql, e);
            String errorMessage = e.getMessage();
            
            // 如果是类型转换错误，提供更友好的提示
            if (errorMessage != null && errorMessage.contains("operator does not exist")) {
                errorMessage = "SQL类型转换错误: " + errorMessage + 
                    "\n提示：PostgreSQL 在比较不同数据类型的字段时需要显式类型转换。" +
                    "\n例如：WHERE id = CAST('123' AS bigint) 或 WHERE id = '123'::bigint";
            }
            
            throw new RuntimeException("执行SQL查询失败: " + errorMessage, e);
        }
        
        return result;
    }
    
    /**
     * 执行 MongoDB 查询（简化实现，实际应该解析SQL并转换为MongoDB查询）
     */
    private QueryResult executeMongoQuery(DataSource dataSource, String sql) {
        // MongoDB 不支持 SQL，这里需要将 SQL 转换为 MongoDB 查询
        // 这是一个简化实现，实际应该使用 SQL 解析器
        throw new UnsupportedOperationException("MongoDB SQL查询功能暂未实现，请使用MongoDB原生查询语法");
    }
    
    /**
     * 查询结果
     */
    public static class QueryResult {
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private int rowCount;
        
        public List<String> getColumns() {
            return columns;
        }
        
        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
        
        public List<Map<String, Object>> getRows() {
            return rows;
        }
        
        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }
        
        public int getRowCount() {
            return rowCount;
        }
        
        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
    }
    
    /**
     * Text2SQL 结果
     */
    public static class Text2SqlResult {
        private String sql;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private int rowCount;
        
        public String getSql() {
            return sql;
        }
        
        public void setSql(String sql) {
            this.sql = sql;
        }
        
        public List<String> getColumns() {
            return columns;
        }
        
        public void setColumns(List<String> columns) {
            this.columns = columns;
        }
        
        public List<Map<String, Object>> getRows() {
            return rows;
        }
        
        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }
        
        public int getRowCount() {
            return rowCount;
        }
        
        public void setRowCount(int rowCount) {
            this.rowCount = rowCount;
        }
    }
}

