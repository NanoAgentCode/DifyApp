package com.github.app.dify.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.domain.DataSource;
import com.github.app.dify.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.service.DatabaseConnectionService;
import com.github.app.dify.service.DatabaseSchemaService;
import com.github.app.dify.service.DataSourceService;
import com.github.app.dify.service.ModelConfigService;
import com.github.app.dify.service.Text2SqlService;
import com.github.app.dify.util.DatabaseDriverManager;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Filters;
import static com.mongodb.client.model.Aggregates.*;
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
public class Text2SqlServiceImpl implements Text2SqlService {
    
    private static final Logger logger = LoggerFactory.getLogger(Text2SqlServiceImpl.class);
    
    @Autowired
    private DataSourceService dataSourceService;
    
    @Autowired
    private DatabaseSchemaService schemaService;
    
    @Autowired
    private DatabaseConnectionService connectionService;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 执行 Text2SQL 查询
     * @param dataSourceId 数据源ID
     * @param question 用户问题
     * @param modelId 模型ID（可选，如果不指定则使用默认模型）
     * @param tableNames 表名列表（可选，如果指定则只使用这些表的结构）
     * @return 查询结果
     */
    @Override
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
            
            // 生成查询语句（SQL或MongoDB查询）
            String query;
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                query = generateMongoQuery(dataSource, question, schemaInfo, modelId);
                logger.info("生成的MongoDB查询 - 数据源ID: {}, 问题: {}, 查询: {}", dataSourceId, question, query);
            } else {
                query = generateSql(dataSource, question, schemaInfo, modelId);
                logger.info("生成的SQL - 数据源ID: {}, SQL: {}", dataSourceId, query);
            }
            
            // 执行查询
            QueryResult result = executeSql(dataSource, dbType, query);
            
            Text2SqlResult text2SqlResult = new Text2SqlResult();
            text2SqlResult.setSql(query);
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
            DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
            
            List<String> targetTables;
            if (tableNames != null && !tableNames.isEmpty()) {
                targetTables = tableNames;
            } else {
                targetTables = schemaService.getTableList(dataSource);
            }
            
            // MongoDB 使用不同的结构
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                // 获取所有集合的结构
                List<Map<String, Object>> collectionSchemas = new ArrayList<>();
                
                for (String collectionName : targetTables) {
                    String schemaJson = schemaService.getTableSchema(dataSource, collectionName, false);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);
                    collectionSchemas.add(schema);
                }
                
                // 构建集合结构信息
                StringBuilder schemaBuilder = new StringBuilder();
                schemaBuilder.append("集合结构信息：\n");
                for (Map<String, Object> schema : collectionSchemas) {
                    schemaBuilder.append(objectMapper.writeValueAsString(schema)).append("\n");
                }
                
                return schemaBuilder.toString();
            } else {
            // 获取所有表的结构
            List<Map<String, Object>> tableSchemas = new ArrayList<>();
            Map<String, List<Map<String, Object>>> tableRelations = new HashMap<>();
            
            for (String tableName : targetTables) {
                String schemaJson = schemaService.getTableSchema(dataSource, tableName, false);
                @SuppressWarnings("unchecked")
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
            }
        } catch (Exception e) {
            logger.error("获取表结构信息失败", e);
            throw new RuntimeException("获取表结构信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成 MongoDB 查询（JSON格式）
     */
    private String generateMongoQuery(DataSource dataSource, String question, String schemaInfo, Long modelId) {
        // 构建提示词
        String prompt = buildMongoPrompt(dataSource, question, schemaInfo);
        
        // 获取问答模型
        com.github.app.dify.domain.QAModel qaModel;
        try {
            if (modelId != null) {
                qaModel = modelConfigService.getQAModelById(modelId);
            } else {
                qaModel = modelConfigService.getDefaultQAModelForRAG();
            }
        } catch (Exception e) {
            logger.error("获取问答模型失败，使用默认模型 - modelId: {}", modelId, e);
            qaModel = modelConfigService.getDefaultQAModelForRAG();
        }
        
        // 创建 LLM 模型
        ModelLanguageModelFactory.ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
        
        // 构建系统消息
        SystemMessage systemMessage = new SystemMessage(buildMongoSystemPrompt());
        
        // 构建用户消息
        UserMessage userMessage = new UserMessage(prompt);
        
        // 调用 LLM 生成 MongoDB 查询
        Response<dev.langchain4j.data.message.AiMessage> response = chatModel.generate(
                Arrays.asList(systemMessage, userMessage));
        
        String query = response.content().text();
        
        // 清理查询（移除可能的代码块标记）
        query = cleanMongoQuery(query);
        
        return query;
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
     * 构建MongoDB系统提示词
     */
    private String buildMongoSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的MongoDB查询生成助手。根据用户的问题和集合结构，生成正确的MongoDB查询JSON。\n");
        sb.append("数据库类型: MongoDB\n");
        sb.append("要求：\n");
        sb.append("1. 只生成查询操作，不要生成INSERT、UPDATE、DELETE、DROP等修改数据的操作\n");
        sb.append("2. 必须返回有效的JSON格式，不要包含其他解释性文字\n");
        sb.append("3. 支持三种查询类型：\n");
        sb.append("   a) find查询（简单查询）：{\"type\": \"find\", \"collection\": \"集合名\", \"filter\": {...}, \"projection\": {...}, \"sort\": {...}, \"limit\": 数量}\n");
        sb.append("   b) aggregate查询（聚合统计）：{\"type\": \"aggregate\", \"collection\": \"集合名\", \"pipeline\": [...]}\n");
        sb.append("   c) metadata查询（元数据查询）：{\"type\": \"metadata\", \"metadataType\": \"collections\" 或 \"collectionCount\"}\n");
        sb.append("4. 如果问题涉及统计（COUNT、SUM、AVG、MAX、MIN、分组等），必须使用aggregate查询\n");
        sb.append("5. 如果只是简单的查询和过滤，使用find查询\n");
        sb.append("6. 如果问题是关于集合列表、集合数量等元数据信息，必须使用metadata查询\n");
        sb.append("   - 查询集合列表：{\"type\": \"metadata\", \"metadataType\": \"collections\"}\n");
        sb.append("   - 查询集合数量：{\"type\": \"metadata\", \"metadataType\": \"collectionCount\"}\n");
        sb.append("\n");
        sb.append("find查询格式：\n");
        sb.append("   {\n");
        sb.append("     \"type\": \"find\",\n");
        sb.append("     \"collection\": \"集合名称\",\n");
        sb.append("     \"filter\": {\"字段名\": \"值\" 或 {\"$操作符\": \"值\"}},\n");
        sb.append("     \"projection\": {\"字段名\": 1 或 0},\n");
        sb.append("     \"sort\": {\"字段名\": 1 或 -1},\n");
        sb.append("     \"limit\": 数量\n");
        sb.append("   }\n");
        sb.append("\n");
        sb.append("aggregate查询格式：\n");
        sb.append("   {\n");
        sb.append("     \"type\": \"aggregate\",\n");
        sb.append("     \"collection\": \"集合名称\",\n");
        sb.append("     \"pipeline\": [\n");
        sb.append("       {\"$match\": {...}},  // 过滤条件（可选）\n");
        sb.append("       {\"$group\": {\"_id\": \"$字段名\", \"count\": {\"$sum\": 1}, \"total\": {\"$sum\": \"$字段名\"}}},  // 分组统计\n");
        sb.append("       {\"$project\": {...}},  // 字段投影（可选）\n");
        sb.append("       {\"$sort\": {...}},  // 排序（可选）\n");
        sb.append("       {\"$limit\": 数量}  // 限制数量（可选）\n");
        sb.append("     ]\n");
        sb.append("   }\n");
        sb.append("\n");
        sb.append("filter字段支持的操作符：\n");
        sb.append("   - $eq: 等于, $ne: 不等于\n");
        sb.append("   - $gt: 大于, $gte: 大于等于, $lt: 小于, $lte: 小于等于\n");
        sb.append("   - $in: 在数组中, $nin: 不在数组中\n");
        sb.append("   - $regex: 正则匹配（需要$options字段，如\"i\"表示忽略大小写）\n");
        sb.append("   - $or: OR条件, $and: AND条件\n");
        sb.append("\n");
        sb.append("aggregate管道阶段：\n");
        sb.append("   - $match: 过滤文档（类似WHERE）\n");
        sb.append("   - $group: 分组统计，必须包含_id字段（分组字段）\n");
        sb.append("     * 统计所有记录数量：{\"_id\": null, \"count\": {\"$sum\": 1}}（_id必须是null，$sum的值必须是数字1）\n");
        sb.append("     * 分组表达式：{\"$sum\": 1} 或 {\"$sum\": \"$字段名\"} 统计数量或求和\n");
        sb.append("     * {\"$avg\": \"$字段名\"} 平均值, {\"$max\": \"$字段名\"} 最大值, {\"$min\": \"$字段名\"} 最小值\n");
        sb.append("     * {\"$count\": true} 或 {\"$sum\": 1} 统计数量\n");
        sb.append("     * 重要：统计记录总数时，_id 必须是 null（不是字符串），$sum 的值必须是数字 1（不是字符串）\n");
        sb.append("   - $project: 字段投影（类似SELECT）\n");
        sb.append("   - $sort: 排序，{\"字段名\": 1} 升序, {\"字段名\": -1} 降序\n");
        sb.append("   - $limit: 限制返回数量\n");
        sb.append("   - $skip: 跳过指定数量\n");
        sb.append("\n");
        sb.append("重要：只能使用集合结构信息中提供的字段名\n");
        sb.append("\n");
        sb.append("示例：\n");
        sb.append("find查询：\n");
        sb.append("   {\"type\": \"find\", \"collection\": \"users\", \"filter\": {\"age\": {\"$gte\": 18}}, \"limit\": 10}\n");
        sb.append("   {\"type\": \"find\", \"collection\": \"orders\", \"filter\": {\"status\": \"completed\"}, \"sort\": {\"createTime\": -1}}\n");
        sb.append("\n");
        sb.append("aggregate查询（统计总数）：\n");
        sb.append("   {\"type\": \"aggregate\", \"collection\": \"orders\", \"pipeline\": [{\"$group\": {\"_id\": null, \"count\": {\"$sum\": 1}}}]}\n");
        sb.append("\n");
        sb.append("aggregate查询（分组统计）：\n");
        sb.append("   {\"type\": \"aggregate\", \"collection\": \"orders\", \"pipeline\": [\n");
        sb.append("     {\"$match\": {\"status\": \"completed\"}},\n");
        sb.append("     {\"$group\": {\"_id\": \"$userId\", \"totalAmount\": {\"$sum\": \"$amount\"}, \"count\": {\"$sum\": 1}}},\n");
        sb.append("     {\"$sort\": {\"totalAmount\": -1}},\n");
        sb.append("     {\"$limit\": 10}\n");
        sb.append("   ]}\n");
        sb.append("\n");
        sb.append("metadata查询（集合数量）：\n");
        sb.append("   {\"type\": \"metadata\", \"metadataType\": \"collectionCount\"}\n");
        sb.append("\n");
        sb.append("metadata查询（集合列表）：\n");
        sb.append("   {\"type\": \"metadata\", \"metadataType\": \"collections\"}\n");
        return sb.toString();
    }
    
    /**
     * 构建MongoDB用户提示词
     */
    private String buildMongoPrompt(DataSource dataSource, String question, String schemaInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("数据库信息：\n");
        sb.append("数据库类型: MongoDB\n");
        sb.append("数据库名称: ").append(dataSource.getDatabase()).append("\n");
        sb.append("\n");
        sb.append("集合结构信息（JSON格式，包含字段名和数据类型）：\n");
        sb.append(schemaInfo);
        sb.append("\n");
        sb.append("重要提示：\n");
        sb.append("- 只能使用上面集合结构信息中列出的字段名\n");
        sb.append("- 如果集合结构信息中没有某个字段，说明该字段不存在，绝对不要使用它\n");
        sb.append("\n");
        sb.append("查询类型选择：\n");
        sb.append("- 如果问题是关于集合列表、集合数量等元数据信息（如：\"有多少个集合\"、\"列出所有集合\"等），必须使用metadata查询\n");
        sb.append("- 如果问题涉及统计、分组、聚合（如：总数、求和、平均值、最大值、最小值、分组统计等），必须使用aggregate查询\n");
        sb.append("- 如果只是简单的查询、过滤、排序，使用find查询\n");
        sb.append("\n");
        sb.append("find查询说明：\n");
        sb.append("- filter条件中，简单等于可以直接写 {\"字段名\": \"值\"}\n");
        sb.append("- filter条件中，复杂条件使用操作符，如 {\"字段名\": {\"$gt\": 100}}\n");
        sb.append("- 多个条件默认是AND关系，如果需要OR关系，使用$or操作符\n");
        sb.append("\n");
        sb.append("aggregate查询说明：\n");
        sb.append("- 统计记录总数（非常重要）：必须使用 {\"$group\": {\"_id\": null, \"count\": {\"$sum\": 1}}}\n");
        sb.append("  * _id 必须是 null（JSON中的null值，不是字符串\"null\"），表示不分组，统计所有记录\n");
        sb.append("  * count 使用 {\"$sum\": 1} 来统计数量（注意：1是数字，不是字符串\"1\"）\n");
        sb.append("  * 示例：{\"type\": \"aggregate\", \"collection\": \"users\", \"pipeline\": [{\"$group\": {\"_id\": null, \"count\": {\"$sum\": 1}}}]}\n");
        sb.append("- 分组统计：使用 {\"$group\": {\"_id\": \"$字段名\", \"count\": {\"$sum\": 1}, \"total\": {\"$sum\": \"$字段名\"}}}\n");
        sb.append("- 平均值：{\"$avg\": \"$字段名\"}, 最大值：{\"$max\": \"$字段名\"}, 最小值：{\"$min\": \"$字段名\"}\n");
        sb.append("- 可以先使用$match过滤，然后$group分组统计，最后$sort排序和$limit限制\n");
        sb.append("- 字段引用使用 \"$字段名\" 格式（注意$符号）\n");
        sb.append("- 重要：统计记录数量时，_id 必须是 null（JSON中的null值），count 的 $sum 值必须是数字 1（不是字符串）\n");
        sb.append("\n");
        sb.append("用户问题：\n");
        sb.append(question);
        sb.append("\n");
        sb.append("请根据以上信息生成MongoDB查询JSON（只使用集合结构信息中提供的字段，根据问题类型选择合适的查询方式，返回有效的JSON格式）：");
        return sb.toString();
    }
    
    /**
     * 清理MongoDB查询（移除代码块标记等）
     */
    private String cleanMongoQuery(String query) {
        if (query == null) {
            return null;
        }
        
        query = query.trim();
        
        // 移除可能的代码块标记
        if (query.startsWith("```json")) {
            query = query.substring(7);
        } else if (query.startsWith("```")) {
            query = query.substring(3);
        }
        if (query.endsWith("```")) {
            query = query.substring(0, query.length() - 3);
        }
        
        return query.trim();
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
                    @SuppressWarnings("unchecked")
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
                    @SuppressWarnings("unchecked")
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
     * 执行 MongoDB 查询
     * @param dataSource 数据源配置
     * @param queryJson MongoDB查询JSON字符串，格式：
     *   find查询: {"type": "find", "collection": "collectionName", "filter": {...}, "projection": {...}, "sort": {...}, "limit": 100}
     *   aggregate查询: {"type": "aggregate", "collection": "collectionName", "pipeline": [...]}
     */
    private QueryResult executeMongoQuery(DataSource dataSource, String queryJson) {
        try {
            // 解析查询JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> query = objectMapper.readValue(queryJson, Map.class);
            
            // 检查查询类型：metadata、aggregate 或 find（默认）
            String queryType = (String) query.getOrDefault("type", "find");
            
            // metadata查询不需要collection字段
            if ("metadata".equalsIgnoreCase(queryType)) {
                return executeMongoMetadata(dataSource, query);
            }
            
            // find和aggregate查询需要collection字段
            String collectionName = (String) query.get("collection");
            if (collectionName == null || collectionName.isEmpty()) {
                throw new IllegalArgumentException("MongoDB查询必须指定collection字段");
            }
            
            // 获取MongoDB数据库
            MongoDatabase database = connectionService.getMongoDatabase(dataSource);
            MongoCollection<Document> collection = database.getCollection(collectionName);
            
            if ("aggregate".equalsIgnoreCase(queryType)) {
                // 执行聚合查询
                return executeMongoAggregate(collection, query);
            } else {
                // 执行find查询（原有逻辑）
                return executeMongoFind(collection, query);
            }
        } catch (Exception e) {
            logger.error("执行MongoDB查询失败 - 查询: {}", queryJson, e);
            throw new RuntimeException("执行MongoDB查询失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行MongoDB find查询
     */
    private QueryResult executeMongoFind(MongoCollection<Document> collection, Map<String, Object> query) {
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try {
            
            // 构建查询条件
            Bson filter = null;
            if (query.containsKey("filter") && query.get("filter") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> filterMap = (Map<String, Object>) query.get("filter");
                filter = buildBsonFilter(filterMap);
            }
            
            // 构建投影（字段选择）
            Bson projection = null;
            if (query.containsKey("projection") && query.get("projection") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> projectionMap = (Map<String, Object>) query.get("projection");
                projection = buildBsonProjection(projectionMap);
            }
            
            // 构建排序
            Bson sort = null;
            if (query.containsKey("sort") && query.get("sort") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sortMap = (Map<String, Object>) query.get("sort");
                sort = buildBsonSort(sortMap);
            }
            
            // 获取限制数量
            Integer limit = null;
            if (query.containsKey("limit") && query.get("limit") != null) {
                if (query.get("limit") instanceof Number) {
                    limit = ((Number) query.get("limit")).intValue();
                } else {
                    limit = Integer.parseInt(query.get("limit").toString());
                }
            }
            
            // 执行查询
            MongoCursor<Document> cursor;
            com.mongodb.client.FindIterable<Document> findIterable;
            
            if (filter != null) {
                findIterable = collection.find(filter);
            } else {
                findIterable = collection.find();
            }
            
            if (projection != null) {
                findIterable = findIterable.projection(projection);
            }
            
            if (sort != null) {
                findIterable = findIterable.sort(sort);
            }
            
            if (limit != null && limit > 0) {
                findIterable = findIterable.limit(limit);
            }
            
            cursor = findIterable.iterator();
            
            // 处理结果
            Set<String> columnSet = new LinkedHashSet<>();
            
            try {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    
                    for (String key : doc.keySet()) {
                        columnSet.add(key);
                        Object value = doc.get(key);
                        // 转换BSON类型为Java类型
                        row.put(key, convertBsonValue(value));
                    }
                    
                    rows.add(row);
                }
            } finally {
                cursor.close();
            }
            
            // 设置列信息
            columns.addAll(columnSet);
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
            
            logger.info("MongoDB find查询执行成功 - 返回记录数: {}", rows.size());
            
        } catch (Exception e) {
            logger.error("执行MongoDB find查询失败", e);
            throw new RuntimeException("执行MongoDB find查询失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 执行MongoDB聚合查询
     */
    private QueryResult executeMongoAggregate(MongoCollection<Document> collection, Map<String, Object> query) {
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try {
            // 获取聚合管道
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pipelineList = (List<Map<String, Object>>) query.get("pipeline");
            
            if (pipelineList == null || pipelineList.isEmpty()) {
                throw new IllegalArgumentException("MongoDB聚合查询必须指定pipeline字段（数组）");
            }
            
            // 检查是否是简单的统计记录数量查询（优化：使用countDocuments更高效）
            if (isSimpleCountQuery(pipelineList)) {
                return executeSimpleCount(collection, pipelineList);
            }
            
            // 构建聚合管道
            List<Bson> pipeline = new ArrayList<>();
            for (Map<String, Object> stage : pipelineList) {
                Bson bsonStage = buildAggregateStage(stage);
                if (bsonStage != null) {
                    pipeline.add(bsonStage);
                }
            }
            
            if (pipeline.isEmpty()) {
                throw new IllegalArgumentException("聚合管道不能为空");
            }
            
            // 记录构建的管道（用于调试）
            logger.info("MongoDB聚合管道 - 阶段数: {}, 原始pipeline: {}", pipeline.size(), pipelineList);
            if (logger.isDebugEnabled()) {
                logger.debug("MongoDB聚合管道BSON - 管道: {}", pipeline);
            }
            
            // 执行聚合查询
            MongoCursor<Document> cursor = collection.aggregate(pipeline).iterator();
            
            // 处理结果
            Set<String> columnSet = new LinkedHashSet<>();
            
            try {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    
                    for (String key : doc.keySet()) {
                        columnSet.add(key);
                        Object value = doc.get(key);
                        // 转换BSON类型为Java类型
                        row.put(key, convertBsonValue(value));
                    }
                    
                    rows.add(row);
                }
            } finally {
                cursor.close();
            }
            
            // 设置列信息
            columns.addAll(columnSet);
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
            
            logger.info("MongoDB聚合查询执行成功 - 管道阶段数: {}, 返回记录数: {}, 结果: {}", pipeline.size(), rows.size(), rows);
            if (logger.isDebugEnabled()) {
                logger.debug("MongoDB聚合查询结果 - 列: {}, 行数据: {}", columns, rows);
            }
            
        } catch (Exception e) {
            logger.error("执行MongoDB聚合查询失败", e);
            throw new RuntimeException("执行MongoDB聚合查询失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 检查是否是简单的统计记录数量查询
     * 格式：pipeline = [{"$group": {"_id": null, "count": {"$sum": 1}}}]
     * 或者：pipeline = [{"$match": {...}}, {"$group": {"_id": null, "count": {"$sum": 1}}}]
     */
    private boolean isSimpleCountQuery(List<Map<String, Object>> pipelineList) {
        if (pipelineList == null || pipelineList.isEmpty()) {
            return false;
        }
        
        // 检查最后一个阶段是否是 $group，且 _id 为 null，只有 count: {$sum: 1}
        Map<String, Object> lastStage = pipelineList.get(pipelineList.size() - 1);
        if (!lastStage.containsKey("$group")) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> groupSpec = (Map<String, Object>) lastStage.get("$group");
        if (groupSpec == null || groupSpec.size() != 2) {
            return false;
        }
        
        // 检查 _id 是否为 null
        Object idValue = groupSpec.get("_id");
        if (idValue != null && !(idValue instanceof String && "null".equalsIgnoreCase((String) idValue))) {
            return false;
        }
        
        // 检查是否有 count: {$sum: 1} 或 count: {$sum: "1"}
        Object countValue = groupSpec.get("count");
        if (!(countValue instanceof Map)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> countExpr = (Map<String, Object>) countValue;
        if (!countExpr.containsKey("$sum")) {
            return false;
        }
        
        Object sumValue = countExpr.get("$sum");
        // 检查 $sum 的值是否为 1（数字或字符串"1"）
        if (sumValue instanceof Number && ((Number) sumValue).intValue() == 1) {
            return true;
        }
        if (sumValue instanceof String && "1".equals(sumValue)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 执行简单的统计记录数量查询（使用countDocuments优化）
     */
    private QueryResult executeSimpleCount(MongoCollection<Document> collection, List<Map<String, Object>> pipelineList) {
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try {
            // 检查是否有 $match 阶段
            Bson filter = null;
            if (pipelineList.size() > 1) {
                Map<String, Object> firstStage = pipelineList.get(0);
                if (firstStage.containsKey("$match")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> matchFilter = (Map<String, Object>) firstStage.get("$match");
                    if (matchFilter != null && !matchFilter.isEmpty()) {
                        filter = buildBsonFilter(matchFilter);
                    }
                }
            }
            
            // 使用 countDocuments 方法（更高效）
            long count;
            if (filter != null) {
                count = collection.countDocuments(filter);
            } else {
                count = collection.countDocuments();
            }
            
            // 构建结果
            columns.add("count");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("count", count);
            rows.add(row);
            
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
            
            logger.info("MongoDB简单统计查询执行成功 - 使用countDocuments优化, 记录数: {}", count);
            
        } catch (Exception e) {
            logger.error("执行MongoDB简单统计查询失败", e);
            throw new RuntimeException("执行MongoDB简单统计查询失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 执行MongoDB元数据查询（集合列表、集合数量等）
     */
    private QueryResult executeMongoMetadata(DataSource dataSource, Map<String, Object> query) {
        QueryResult result = new QueryResult();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        try {
            String metadataType = (String) query.getOrDefault("metadataType", "collections");
            
            MongoDatabase database = connectionService.getMongoDatabase(dataSource);
            
            if ("collections".equalsIgnoreCase(metadataType) || "collectionCount".equalsIgnoreCase(metadataType)) {
                // 获取集合列表
                List<String> collectionNames = database.listCollectionNames().into(new ArrayList<>());
                
                if ("collectionCount".equalsIgnoreCase(metadataType)) {
                    // 只返回集合数量
                    columns.add("count");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("count", collectionNames.size());
                    rows.add(row);
                } else {
                    // 返回集合列表
                    columns.add("name");
                    for (String name : collectionNames) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("name", name);
                        rows.add(row);
                    }
                }
            } else {
                throw new IllegalArgumentException("不支持的元数据查询类型: " + metadataType);
            }
            
            result.setColumns(columns);
            result.setRows(rows);
            result.setRowCount(rows.size());
            
            logger.info("MongoDB元数据查询执行成功 - 类型: {}, 返回记录数: {}", metadataType, rows.size());
            
        } catch (Exception e) {
            logger.error("执行MongoDB元数据查询失败", e);
            throw new RuntimeException("执行MongoDB元数据查询失败: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 构建聚合管道阶段
     */
    private Bson buildAggregateStage(Map<String, Object> stage) {
        if (stage == null || stage.isEmpty()) {
            return null;
        }
        
        // 检查阶段类型（$match, $group, $project, $sort, $limit, $skip等）
        if (stage.containsKey("$match")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> matchFilter = (Map<String, Object>) stage.get("$match");
            return match(matchFilter != null ? buildBsonFilter(matchFilter) : new Document());
        }
        
        if (stage.containsKey("$group")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> groupSpec = (Map<String, Object>) stage.get("$group");
            Document groupDoc = new Document();
            
            for (Map.Entry<String, Object> entry : groupSpec.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // 特殊处理 _id 字段：null 值需要特殊处理
                if ("_id".equals(key)) {
                    if (value == null) {
                        groupDoc.append(key, null);
                    } else if (value instanceof String && "null".equalsIgnoreCase((String) value)) {
                        // 处理字符串 "null" 的情况
                        groupDoc.append(key, null);
                    } else {
                        groupDoc.append(key, value);
                    }
                } else if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> expr = (Map<String, Object>) value;
                    groupDoc.append(key, buildGroupExpression(expr));
                } else {
                    groupDoc.append(key, value);
                }
            }
            
            return group(groupDoc);
        }
        
        if (stage.containsKey("$project")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> projectSpec = (Map<String, Object>) stage.get("$project");
            return project(projectSpec != null ? buildBsonProjection(projectSpec) : new Document());
        }
        
        if (stage.containsKey("$sort")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sortSpec = (Map<String, Object>) stage.get("$sort");
            return sort(sortSpec != null ? buildBsonSort(sortSpec) : new Document());
        }
        
        if (stage.containsKey("$limit")) {
            Object limitObj = stage.get("$limit");
            int limit = limitObj instanceof Number ? ((Number) limitObj).intValue() : Integer.parseInt(limitObj.toString());
            return limit(limit);
        }
        
        if (stage.containsKey("$skip")) {
            Object skipObj = stage.get("$skip");
            int skip = skipObj instanceof Number ? ((Number) skipObj).intValue() : Integer.parseInt(skipObj.toString());
            return skip(skip);
        }
        
        // 如果不匹配任何已知阶段，返回Document
        return new Document(stage);
    }
    
    /**
     * 构建分组表达式（$sum, $avg, $max, $min, $count等）
     */
    private Object buildGroupExpression(Map<String, Object> expr) {
        if (expr.containsKey("$sum")) {
            Object sumValue = expr.get("$sum");
            if (sumValue instanceof Number) {
                return new Document("$sum", sumValue);
            } else if (sumValue instanceof String) {
                String strValue = (String) sumValue;
                // 尝试将字符串转换为数字（处理 "1" -> 1 的情况）
                try {
                    if (strValue.matches("^-?\\d+$")) {
                        // 是纯数字字符串，转换为整数
                        int intValue = Integer.parseInt(strValue);
                        return new Document("$sum", intValue);
                    } else if (strValue.matches("^-?\\d+\\.\\d+$")) {
                        // 是浮点数字符串，转换为浮点数
                        double doubleValue = Double.parseDouble(strValue);
                        return new Document("$sum", doubleValue);
                    } else {
                        // 不是数字，可能是字段引用
                        return new Document("$sum", strValue.startsWith("$") ? strValue : "$" + strValue);
                    }
                } catch (NumberFormatException e) {
                    // 转换失败，当作字段引用处理
                    return new Document("$sum", strValue.startsWith("$") ? strValue : "$" + strValue);
                }
            } else {
                return new Document("$sum", sumValue);
            }
        }
        
        if (expr.containsKey("$avg")) {
            Object avgValue = expr.get("$avg");
            if (avgValue instanceof String) {
                String strValue = (String) avgValue;
                return new Document("$avg", strValue.startsWith("$") ? strValue : "$" + strValue);
            } else {
                return new Document("$avg", avgValue);
            }
        }
        
        if (expr.containsKey("$max")) {
            Object maxValue = expr.get("$max");
            if (maxValue instanceof String) {
                String strValue = (String) maxValue;
                return new Document("$max", strValue.startsWith("$") ? strValue : "$" + strValue);
            } else {
                return new Document("$max", maxValue);
            }
        }
        
        if (expr.containsKey("$min")) {
            Object minValue = expr.get("$min");
            if (minValue instanceof String) {
                String strValue = (String) minValue;
                return new Document("$min", strValue.startsWith("$") ? strValue : "$" + strValue);
            } else {
                return new Document("$min", minValue);
            }
        }
        
        if (expr.containsKey("$count")) {
            return new Document("$sum", 1);
        }
        
        if (expr.containsKey("$first")) {
            Object firstValue = expr.get("$first");
            if (firstValue instanceof String) {
                String strValue = (String) firstValue;
                return new Document("$first", strValue.startsWith("$") ? strValue : "$" + strValue);
            } else {
                return new Document("$first", firstValue);
            }
        }
        
        if (expr.containsKey("$last")) {
            Object lastValue = expr.get("$last");
            if (lastValue instanceof String) {
                String strValue = (String) lastValue;
                return new Document("$last", strValue.startsWith("$") ? strValue : "$" + strValue);
            } else {
                return new Document("$last", lastValue);
            }
        }
        
        // 默认返回原表达式
        return new Document(expr);
    }
    
    /**
     * 转换BSON值为Java可序列化的类型
     * 处理ObjectId、Document、数组、嵌套对象等复杂类型
     */
    private Object convertBsonValue(Object value) {
        if (value == null) {
            return null;
        }
        
        // ObjectId 转换为字符串
        if (value instanceof org.bson.types.ObjectId) {
            return value.toString();
        }
        
        // Date 保持原样（Jackson会自动序列化为时间戳或ISO格式）
        if (value instanceof java.util.Date) {
            return value;
        }
        
        // Document 转换为 Map
        if (value instanceof Document) {
            Document doc = (Document) value;
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : doc.keySet()) {
                map.put(key, convertBsonValue(doc.get(key)));
            }
            return map;
        }
        
        // 数组/列表 递归转换每个元素
        if (value instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            List<Object> convertedList = new ArrayList<>();
            for (Object item : list) {
                convertedList.add(convertBsonValue(item));
            }
            return convertedList;
        }
        
        // Map类型（可能是嵌套的Map）递归转换
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, Object> convertedMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                convertedMap.put(entry.getKey(), convertBsonValue(entry.getValue()));
            }
            return convertedMap;
        }
        
        // 其他BSON类型（如Binary、Decimal128等）转换为字符串
        if (value.getClass().getPackage() != null && 
            value.getClass().getPackage().getName().startsWith("org.bson")) {
            return value.toString();
        }
        
        // 基本类型（String、Number、Boolean等）直接返回
        return value;
    }
    
    /**
     * 构建BSON过滤条件
     */
    private Bson buildBsonFilter(Map<String, Object> filterMap) {
        // 检查是否有逻辑操作符（$or, $and, $nor）
        if (filterMap.containsKey("$or")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> orConditions = (List<Map<String, Object>>) filterMap.get("$or");
            List<Bson> orFilters = new ArrayList<>();
            for (Map<String, Object> condition : orConditions) {
                orFilters.add(buildBsonFilter(condition));
            }
            return Filters.or(orFilters);
        }
        
        if (filterMap.containsKey("$and")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> andConditions = (List<Map<String, Object>>) filterMap.get("$and");
            List<Bson> andFilters = new ArrayList<>();
            for (Map<String, Object> condition : andConditions) {
                andFilters.add(buildBsonFilter(condition));
            }
            return Filters.and(andFilters);
        }
        
        if (filterMap.containsKey("$nor")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> norConditions = (List<Map<String, Object>>) filterMap.get("$nor");
            List<Bson> norFilters = new ArrayList<>();
            for (Map<String, Object> condition : norConditions) {
                norFilters.add(buildBsonFilter(condition));
            }
            return Filters.nor(norFilters);
        }
        
        // 处理普通字段条件
        List<Bson> filters = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> operatorMap = (Map<String, Object>) value;
                
                // 处理操作符：$eq, $ne, $gt, $gte, $lt, $lte, $in, $nin, $regex等
                if (operatorMap.containsKey("$eq")) {
                    filters.add(Filters.eq(key, operatorMap.get("$eq")));
                } else if (operatorMap.containsKey("$ne")) {
                    filters.add(Filters.ne(key, operatorMap.get("$ne")));
                } else if (operatorMap.containsKey("$gt")) {
                    filters.add(Filters.gt(key, operatorMap.get("$gt")));
                } else if (operatorMap.containsKey("$gte")) {
                    filters.add(Filters.gte(key, operatorMap.get("$gte")));
                } else if (operatorMap.containsKey("$lt")) {
                    filters.add(Filters.lt(key, operatorMap.get("$lt")));
                } else if (operatorMap.containsKey("$lte")) {
                    filters.add(Filters.lte(key, operatorMap.get("$lte")));
                } else if (operatorMap.containsKey("$in")) {
                    @SuppressWarnings("unchecked")
                    List<Object> inList = (List<Object>) operatorMap.get("$in");
                    filters.add(Filters.in(key, inList));
                } else if (operatorMap.containsKey("$nin")) {
                    @SuppressWarnings("unchecked")
                    List<Object> ninList = (List<Object>) operatorMap.get("$nin");
                    filters.add(Filters.nin(key, ninList));
                } else if (operatorMap.containsKey("$regex")) {
                    String pattern = (String) operatorMap.get("$regex");
                    String options = (String) operatorMap.getOrDefault("$options", "");
                    filters.add(Filters.regex(key, pattern, options));
                } else if (operatorMap.containsKey("$exists")) {
                    boolean exists = Boolean.TRUE.equals(operatorMap.get("$exists"));
                    filters.add(Filters.exists(key, exists));
                } else {
                    // 默认使用等于
                    filters.add(Filters.eq(key, value));
                }
            } else {
                // 简单等于条件
                filters.add(Filters.eq(key, value));
            }
        }
        
        if (filters.size() == 1) {
            return filters.get(0);
        } else if (filters.size() > 1) {
            return Filters.and(filters);
        } else {
            return new Document(); // 空过滤条件
        }
    }
    
    /**
     * 构建BSON投影（字段选择）
     */
    private Bson buildBsonProjection(Map<String, Object> projectionMap) {
        Document projection = new Document();
        for (Map.Entry<String, Object> entry : projectionMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) {
                projection.append(entry.getKey(), ((Number) value).intValue());
            } else if (value instanceof Boolean) {
                projection.append(entry.getKey(), (Boolean) value);
            } else {
                projection.append(entry.getKey(), 1);
            }
        }
        return projection;
    }
    
    /**
     * 构建BSON排序
     */
    private Bson buildBsonSort(Map<String, Object> sortMap) {
        Document sort = new Document();
        for (Map.Entry<String, Object> entry : sortMap.entrySet()) {
            Object value = entry.getValue();
            int direction = 1; // 默认升序
            if (value instanceof Number) {
                direction = ((Number) value).intValue();
            } else if (value instanceof String) {
                String dir = ((String) value).toLowerCase();
                if ("desc".equals(dir) || "-1".equals(dir)) {
                    direction = -1;
                }
            }
            sort.append(entry.getKey(), direction);
        }
        return sort;
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
}