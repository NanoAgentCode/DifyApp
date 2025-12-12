package com.github.app.dify.appsystemdata.service;

import com.github.app.dify.appsystemdata.domain.DataSource;
import com.mongodb.client.MongoDatabase;
import java.sql.Connection;
import java.sql.SQLException;
/**
 * 数据库连接管理服务接口
 * 支持连接池（常用数据源）和按需创建连接（其他数据源）
 */
public interface DatabaseConnectionService {
    
    /**
     * 获取数据库连接
     * @param dataSource 数据源配置
     * @return 数据库连接
     */
    Connection getConnection(DataSource dataSource) throws SQLException;
    
    /**
     * 获取 MongoDB 数据库
     * @param dataSource 数据源配置
     * @return MongoDB 数据库
     */
    MongoDatabase getMongoDatabase(DataSource dataSource);
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @return 是否连接成功
     */
    boolean testConnection(DataSource dataSource);
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @param passwordEncrypted 密码是否已加密
     * @return 是否连接成功
     */
    boolean testConnection(DataSource dataSource, boolean passwordEncrypted);
    
    /**
     * 清除数据源的连接池（当数据源配置更新时调用）
     * @param dataSourceId 数据源ID
     */
    void clearConnectionPool(Long dataSourceId);
}