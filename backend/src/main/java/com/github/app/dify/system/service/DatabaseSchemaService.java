package com.github.app.dify.system.service;

import com.github.app.dify.system.domain.DataSource;
import java.util.List;
import java.util.Map;
/**
 * 数据库表结构服务接口
 * 负责获取和缓存数据库表结构信息
 */
public interface DatabaseSchemaService {
    
    /**
     * 获取数据库表列表
     * @param dataSource 数据源配置
     * @return 表名列表
     */
    List<String> getTableList(DataSource dataSource);
    
    /**
     * 获取表结构信息
     * @param dataSource 数据源配置
     * @param tableName 表名
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return 表结构信息（JSON格式）
     */
    String getTableSchema(DataSource dataSource, String tableName, boolean forceRefresh);
    
    /**
     * 刷新表结构（清除缓存并重新获取）
     * @param dataSource 数据源配置
     * @param tableName 表名（如果为null，则刷新所有表）
     */
    void refreshSchema(DataSource dataSource, String tableName);
    
    /**
     * 保存表结构到缓存
     */
    void saveSchemaToCache(Long dataSourceId, String tableName, Map<String, Object> schemaInfo);
}