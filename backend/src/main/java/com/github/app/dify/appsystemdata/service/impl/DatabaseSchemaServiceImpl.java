package com.github.app.dify.appsystemdata.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.appsystemdata.domain.DataSource;
import com.github.app.dify.appsystemdata.domain.TableSchemaCache;
import com.github.app.dify.appsystemdata.repository.TableSchemaCacheRepository;
import com.github.app.dify.appsystemdata.service.DatabaseConnectionService;
import com.github.app.dify.appsystemdata.service.DatabaseSchemaService;
import com.github.app.dify.appsystemdata.util.DatabaseDriverManager;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.util.*;
import java.util.Date;
/**
 * 数据库表结构服务
 * 负责获取和缓存数据库表结构信息
 */
@Service
public class DatabaseSchemaServiceImpl implements DatabaseSchemaService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseSchemaServiceImpl.class);
    
    @Autowired
    private DatabaseConnectionService connectionService;
    
    @Autowired
    private TableSchemaCacheRepository schemaCacheRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取数据库表列表
     * @param dataSource 数据源配置
     * @return 表名列表
     */
    @Override
    public List<String> getTableList(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        
        try {
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                return getMongoCollectionList(dataSource);
            } else {
                return getJdbcTableList(dataSource, dbType);
            }
        } catch (Exception e) {
            logger.error("获取表列表失败 - 数据源ID: {}", dataSource.getId(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取表结构信息
     * @param dataSource 数据源配置
     * @param tableName 表名
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return 表结构信息（JSON格式）
     */
    @Override
    public String getTableSchema(DataSource dataSource, String tableName, boolean forceRefresh) {
        if (dataSource == null || tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("数据源和表名不能为空");
        }
        
        // 如果不强制刷新，先尝试从缓存获取
        if (!forceRefresh) {
            Optional<TableSchemaCache> cache = schemaCacheRepository.findByDataSourceIdAndTableName(
                    dataSource.getId(), tableName);
            if (cache.isPresent()) {
                logger.debug("从缓存获取表结构 - 数据源ID: {}, 表名: {}", dataSource.getId(), tableName);
                return cache.get().getSchemaInfo();
            }
        }
        
        // 从数据库获取表结构
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        Map<String, Object> schemaInfo;
        
        try {
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                schemaInfo = getMongoSchema(dataSource, tableName);
            } else {
                schemaInfo = getJdbcSchema(dataSource, dbType, tableName);
            }
            
            // 保存到缓存
            saveSchemaToCache(dataSource.getId(), tableName, schemaInfo);
            
            return objectMapper.writeValueAsString(schemaInfo);
        } catch (Exception e) {
            logger.error("获取表结构失败 - 数据源ID: {}, 表名: {}", dataSource.getId(), tableName, e);
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 刷新表结构（清除缓存并重新获取）
     * @param dataSource 数据源配置
     * @param tableName 表名（如果为null，则刷新所有表）
     */
    @Transactional
    @Override
    public void refreshSchema(DataSource dataSource, String tableName) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        if (tableName != null && !tableName.isEmpty()) {
            // 刷新单个表
            schemaCacheRepository.deleteByDataSourceIdAndTableName(dataSource.getId(), tableName);
            getTableSchema(dataSource, tableName, true);
            logger.info("刷新表结构成功 - 数据源ID: {}, 表名: {}", dataSource.getId(), tableName);
        } else {
            // 刷新所有表
            schemaCacheRepository.deleteByDataSourceId(dataSource.getId());
            List<String> tables = getTableList(dataSource);
            for (String table : tables) {
                getTableSchema(dataSource, table, true);
            }
            logger.info("刷新所有表结构成功 - 数据源ID: {}, 表数量: {}", dataSource.getId(), tables.size());
        }
    }
    
    /**
     * 获取 JDBC 数据库表列表
     */
    private List<String> getJdbcTableList(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType) throws SQLException {
        List<String> tables = new ArrayList<>();
        
        try (Connection connection = connectionService.getConnection(dataSource)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            String catalog = null;
            String schemaPattern = null;
            String tableNamePattern = null;
            String[] types = {"TABLE", "VIEW"};
            
            // 不同数据库的 schema 处理方式不同
            if (dbType == DatabaseDriverManager.DatabaseType.ORACLE) {
                schemaPattern = dataSource.getUsername().toUpperCase();
            } else if (dbType == DatabaseDriverManager.DatabaseType.MYSQL) {
                catalog = dataSource.getDatabase();
            } else if (dbType == DatabaseDriverManager.DatabaseType.POSTGRESQL) {
                schemaPattern = "public";
            }
            
            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, tableNamePattern, types)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
        }
        
        return tables;
    }
    
    /**
     * 获取 MongoDB 集合列表
     */
    private List<String> getMongoCollectionList(DataSource dataSource) {
        MongoDatabase database = connectionService.getMongoDatabase(dataSource);
        return database.listCollectionNames().into(new ArrayList<>());
    }
    
    /**
     * 获取 JDBC 表结构
     */
    private Map<String, Object> getJdbcSchema(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType, String tableName) throws SQLException {
        Map<String, Object> schema = new HashMap<>();
        schema.put("tableName", tableName);
        schema.put("databaseType", dbType.getType());
        
        List<Map<String, Object>> columns = new ArrayList<>();
        List<Map<String, Object>> primaryKeys = new ArrayList<>();
        List<Map<String, Object>> foreignKeys = new ArrayList<>();
        
        try (Connection connection = connectionService.getConnection(dataSource)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            String catalog = null;
            String schemaPattern = null;
            
            // 不同数据库的 schema 处理方式不同
            if (dbType == DatabaseDriverManager.DatabaseType.ORACLE) {
                schemaPattern = dataSource.getUsername().toUpperCase();
            } else if (dbType == DatabaseDriverManager.DatabaseType.MYSQL) {
                catalog = dataSource.getDatabase();
            } else if (dbType == DatabaseDriverManager.DatabaseType.POSTGRESQL) {
                schemaPattern = "public";
            }
            
            // 先获取主键信息，用于后续过滤
            Set<String> primaryKeyColumns = new HashSet<>();
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
                while (rs.next()) {
                    primaryKeyColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    Map<String, Object> pk = new HashMap<>();
                    pk.put("columnName", rs.getString("COLUMN_NAME"));
                    pk.put("keySeq", rs.getInt("KEY_SEQ"));
                    pk.put("pkName", rs.getString("PK_NAME"));
                    primaryKeys.add(pk);
                }
            }
            
            // 获取列信息
            // 定义系统列列表（这些列不应该在用户查询中使用）
            Set<String> systemColumns = new HashSet<>(Arrays.asList(
                "tenant_id", "deleted", "create_time", "update_time", 
                "create_by", "update_by", "version", "status"
            ));
            
            try (ResultSet rs = metaData.getColumns(catalog, schemaPattern, tableName, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String columnNameLower = columnName.toLowerCase();
                    
                    // 过滤系统列（但保留主键列，因为用户可能需要通过主键查询）
                    if (systemColumns.contains(columnNameLower) && !primaryKeyColumns.contains(columnNameLower)) {
                        // 如果是系统列但不是主键，则跳过
                        continue;
                    }
                    
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", columnName);
                    column.put("type", rs.getString("TYPE_NAME"));
                    column.put("size", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.put("defaultValue", rs.getString("COLUMN_DEF"));
                    columns.add(column);
                }
            }
            
            // 获取外键信息
            try (ResultSet rs = metaData.getImportedKeys(catalog, schemaPattern, tableName)) {
                while (rs.next()) {
                    Map<String, Object> fk = new HashMap<>();
                    fk.put("columnName", rs.getString("FKCOLUMN_NAME"));
                    fk.put("pkTableName", rs.getString("PKTABLE_NAME"));
                    fk.put("pkColumnName", rs.getString("PKCOLUMN_NAME"));
                    fk.put("fkName", rs.getString("FK_NAME"));
                    foreignKeys.add(fk);
                }
            }
        }
        
        schema.put("columns", columns);
        schema.put("primaryKeys", primaryKeys);
        schema.put("foreignKeys", foreignKeys);
        
        return schema;
    }
    
    /**
     * 获取 MongoDB 集合结构
     */
    private Map<String, Object> getMongoSchema(DataSource dataSource, String collectionName) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("collectionName", collectionName);
        schema.put("databaseType", "mongodb");
        
        MongoDatabase database = connectionService.getMongoDatabase(dataSource);
        org.bson.Document sampleDoc = database.getCollection(collectionName).find().first();
        
        List<Map<String, Object>> fields = new ArrayList<>();
        if (sampleDoc != null) {
            for (String key : sampleDoc.keySet()) {
                Map<String, Object> field = new HashMap<>();
                field.put("name", key);
                Object value = sampleDoc.get(key);
                field.put("type", value != null ? value.getClass().getSimpleName() : "null");
                fields.add(field);
            }
        }
        
        schema.put("fields", fields);
        
        return schema;
    }
    
    /**
     * 保存表结构到缓存
     */
    @Transactional
    @Override
    public void saveSchemaToCache(Long dataSourceId, String tableName, Map<String, Object> schemaInfo) {
        try {
            Optional<TableSchemaCache> existing = schemaCacheRepository.findByDataSourceIdAndTableName(
                    dataSourceId, tableName);
            
            TableSchemaCache cache;
            if (existing.isPresent()) {
                cache = existing.get();
            } else {
                cache = new TableSchemaCache();
                cache.setDataSourceId(dataSourceId);
                cache.setTableName(tableName);
                cache.setCreateTime(new Date());
            }
            
            cache.setSchemaInfo(objectMapper.writeValueAsString(schemaInfo));
            cache.setLastRefreshTime(new Date());
            cache.setUpdateTime(new Date());
            
            schemaCacheRepository.save(cache);
        } catch (Exception e) {
            logger.error("保存表结构缓存失败 - 数据源ID: {}, 表名: {}", dataSourceId, tableName, e);
        }
    }
}