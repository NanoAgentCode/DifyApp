package com.github.app.dify.datasource.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
/**
 * 数据库驱动管理器
 * 负责加载数据库驱动和创建连接
 */
@Component
public class DatabaseDriverManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseDriverManager.class);
    
    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        POSTGRESQL("postgresql", "org.postgresql.Driver", "jdbc:postgresql://%s:%d/%s"),
        MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8"),
        ORACLE("oracle", "oracle.jdbc.driver.OracleDriver", "jdbc:oracle:thin:@%s:%d:%s"),
        MONGODB("mongodb", null, "mongodb://%s:%d/%s"), // MongoDB 不使用 JDBC
        NEO4J("neo4j", null, "bolt://%s:%d"); // Neo4j 使用 Bolt 协议，不使用 JDBC
        
        private final String type;
        private final String driverClass;
        private final String urlTemplate;
        
        DatabaseType(String type, String driverClass, String urlTemplate) {
            this.type = type;
            this.driverClass = driverClass;
            this.urlTemplate = urlTemplate;
        }
        
        public String getType() {
            return type;
        }
        
        public String getDriverClass() {
            return driverClass;
        }
        
        public String getUrlTemplate() {
            return urlTemplate;
        }
        
        public static DatabaseType fromString(String type) {
            if (type == null) {
                return null;
            }
            type = type.toLowerCase();
            for (DatabaseType dbType : values()) {
                if (dbType.type.equals(type)) {
                    return dbType;
                }
            }
            throw new IllegalArgumentException("不支持的数据库类型: " + type);
        }
    }
    
    /**
     * 加载数据库驱动
     * @param databaseType 数据库类型
     */
    public void loadDriver(DatabaseType databaseType) {
        if (databaseType == null || databaseType == DatabaseType.MONGODB || databaseType == DatabaseType.NEO4J) {
            return; // MongoDB 和 Neo4j 不使用 JDBC 驱动
        }
        
        try {
            Class.forName(databaseType.getDriverClass());
            logger.debug("数据库驱动加载成功: {}", databaseType.getDriverClass());
        } catch (ClassNotFoundException e) {
            logger.error("数据库驱动加载失败: {}", databaseType.getDriverClass(), e);
            throw new RuntimeException("数据库驱动加载失败: " + databaseType.getDriverClass(), e);
        }
    }
    
    /**
     * 构建 JDBC URL
     * @param databaseType 数据库类型
     * @param host 主机地址
     * @param port 端口号
     * @param database 数据库名称
     * @return JDBC URL
     */
    public String buildJdbcUrl(DatabaseType databaseType, String host, Integer port, String database) {
        if (databaseType == null) {
            throw new IllegalArgumentException("数据库类型不能为空");
        }
        
        if (databaseType == DatabaseType.MONGODB) {
            // MongoDB 使用特殊的连接字符串格式
            return String.format("mongodb://%s:%d/%s", host, port != null ? port : 27017, database != null ? database : "");
        }
        
        if (databaseType == DatabaseType.NEO4J) {
            // Neo4j 使用 Bolt 协议
            return String.format("bolt://%s:%d", host, port != null ? port : 7687);
        }
        
        // 对于 Oracle，数据库名称实际上是 SID 或 Service Name
        if (databaseType == DatabaseType.ORACLE) {
            return String.format(databaseType.getUrlTemplate(), host, port != null ? port : 1521, database != null ? database : "XE");
        }
        
        // PostgreSQL 和 MySQL
        return String.format(databaseType.getUrlTemplate(), host, port != null ? port : getDefaultPort(databaseType), database != null ? database : "");
    }
    
    /**
     * 获取默认端口
     */
    private int getDefaultPort(DatabaseType databaseType) {
        switch (databaseType) {
            case POSTGRESQL:
                return 5432;
            case MYSQL:
                return 3306;
            case ORACLE:
                return 1521;
            case MONGODB:
                return 27017;
            case NEO4J:
                return 7687;
            default:
                return 3306;
        }
    }
    
    /**
     * 创建数据库连接（JDBC）
     * @param databaseType 数据库类型
     * @param url JDBC URL
     * @param username 用户名
     * @param password 密码
     * @return 数据库连接
     */
    public Connection createConnection(DatabaseType databaseType, String url, String username, String password) throws SQLException {
        if (databaseType == DatabaseType.MONGODB) {
            throw new UnsupportedOperationException("MongoDB 不支持 JDBC 连接，请使用 MongoDB 客户端");
        }
        if (databaseType == DatabaseType.NEO4J) {
            throw new UnsupportedOperationException("Neo4j 不支持 JDBC 连接，请使用 Neo4j 驱动");
        }
        
        // 加载驱动
        loadDriver(databaseType);
        
        // 创建连接
        return DriverManager.getConnection(url, username, password);
    }
    
    /**
     * 测试数据库连接
     * @param databaseType 数据库类型
     * @param url JDBC URL
     * @param username 用户名
     * @param password 密码
     * @return 是否连接成功
     */
    public boolean testConnection(DatabaseType databaseType, String url, String username, String password) {
        if (databaseType == DatabaseType.MONGODB) {
            // MongoDB 连接测试需要特殊处理
            return testMongoConnection(url, username, password);
        }
        if (databaseType == DatabaseType.NEO4J) {
            // Neo4j 连接测试需要特殊处理
            return testNeo4jConnection(url, username, password);
        }
        
        try (Connection connection = createConnection(databaseType, url, username, password)) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.error("数据库连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 测试 MongoDB 连接
     */
    private boolean testMongoConnection(String url, String username, String password) {
        try {
            // 这里需要使用 MongoDB Java Driver 来测试连接
            // 暂时返回 true，实际实现会在 DatabaseConnectionService 中处理
            return true;
        } catch (Exception e) {
            logger.error("MongoDB 连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 测试 Neo4j 连接
     */
    private boolean testNeo4jConnection(String url, String username, String password) {
        try {
            // 这里需要使用 Neo4j Java Driver 来测试连接
            // 暂时返回 true，实际实现会在 DatabaseConnectionService 中处理
            return true;
        } catch (Exception e) {
            logger.error("Neo4j 连接测试失败", e);
            return false;
        }
    }
}

