package com.github.app.dify.service;

import com.github.app.dify.domain.DataSource;
import com.github.app.dify.util.DatabaseDriverManager;
import com.github.app.dify.util.PasswordEncryptionUtil;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 数据库连接管理服务
 * 支持连接池（常用数据源）和按需创建连接（其他数据源）
 */
@Service
public class DatabaseConnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionService.class);
    
    @Autowired
    private PasswordEncryptionUtil passwordEncryptionUtil;
    
    @Autowired
    private DatabaseDriverManager driverManager;
    
    // 连接池缓存（常用数据源）
    private final Map<Long, javax.sql.DataSource> connectionPoolCache = new ConcurrentHashMap<>();
    
    // MongoDB 客户端缓存
    private final Map<Long, MongoClient> mongoClientCache = new ConcurrentHashMap<>();
    
    // 数据源访问频率统计（用于决定是否使用连接池）
    private final Map<Long, Integer> accessFrequency = new ConcurrentHashMap<>();
    
    // 连接池阈值：访问次数超过此值的数据源使用连接池
    private static final int POOL_THRESHOLD = 10;
    
    /**
     * 获取数据库连接
     * @param dataSource 数据源配置
     * @return 数据库连接
     */
    public Connection getConnection(DataSource dataSource) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        
        if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
            throw new UnsupportedOperationException("MongoDB 不支持 JDBC 连接，请使用 getMongoDatabase 方法");
        }
        
        // 更新访问频率
        updateAccessFrequency(dataSource.getId());
        
        // 判断是否使用连接池
        if (shouldUseConnectionPool(dataSource.getId())) {
            return getConnectionFromPool(dataSource, dbType);
        } else {
            return createConnectionOnDemand(dataSource, dbType);
        }
    }
    
    /**
     * 获取 MongoDB 数据库
     * @param dataSource 数据源配置
     * @return MongoDB 数据库
     */
    public MongoDatabase getMongoDatabase(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.MONGODB) {
            throw new IllegalArgumentException("数据源类型不是 MongoDB");
        }
        
        // 更新访问频率
        updateAccessFrequency(dataSource.getId());
        
        // 获取或创建 MongoDB 客户端
        MongoClient mongoClient = mongoClientCache.computeIfAbsent(dataSource.getId(), id -> {
            String password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
            String connectionString = buildMongoConnectionString(dataSource, password);
            logger.info("创建 MongoDB 客户端 - 数据源ID: {}, 连接字符串: {}", id, maskConnectionString(connectionString));
            return MongoClients.create(connectionString);
        });
        
        String databaseName = dataSource.getDatabase();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = "admin"; // 默认数据库
        }
        
        return mongoClient.getDatabase(databaseName);
    }
    
    /**
     * 从连接池获取连接
     */
    private Connection getConnectionFromPool(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType) throws SQLException {
        javax.sql.DataSource pool = connectionPoolCache.computeIfAbsent(dataSource.getId(), id -> {
            logger.info("创建数据源连接池 - 数据源ID: {}, 类型: {}", id, dbType.getType());
            return createConnectionPool(dataSource, dbType);
        });
        
        return pool.getConnection();
    }
    
    /**
     * 按需创建连接
     */
    private Connection createConnectionOnDemand(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType) throws SQLException {
        String password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
        String url = driverManager.buildJdbcUrl(dbType, dataSource.getHost(), dataSource.getPort(), dataSource.getDatabase());
        
        logger.debug("按需创建数据库连接 - 数据源ID: {}, 类型: {}", dataSource.getId(), dbType.getType());
        return driverManager.createConnection(dbType, url, dataSource.getUsername(), password);
    }
    
    /**
     * 创建连接池
     */
    private javax.sql.DataSource createConnectionPool(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType) {
        String password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
        String url = driverManager.buildJdbcUrl(dbType, dataSource.getHost(), dataSource.getPort(), dataSource.getDatabase());
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(dataSource.getUsername());
        config.setPassword(password);
        config.setDriverClassName(dbType.getDriverClass());
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("DataSourcePool-" + dataSource.getId());
        
        return new HikariDataSource(config);
    }
    
    /**
     * 构建 MongoDB 连接字符串
     */
    private String buildMongoConnectionString(DataSource dataSource, String password) {
        StringBuilder sb = new StringBuilder("mongodb://");
        
        if (dataSource.getUsername() != null && !dataSource.getUsername().isEmpty()) {
            sb.append(dataSource.getUsername());
            if (password != null && !password.isEmpty()) {
                sb.append(":").append(password);
            }
            sb.append("@");
        }
        
        sb.append(dataSource.getHost());
        if (dataSource.getPort() != null) {
            sb.append(":").append(dataSource.getPort());
        }
        
        if (dataSource.getDatabase() != null && !dataSource.getDatabase().isEmpty()) {
            sb.append("/").append(dataSource.getDatabase());
        }
        
        return sb.toString();
    }
    
    /**
     * 掩码连接字符串（用于日志）
     */
    private String maskConnectionString(String connectionString) {
        if (connectionString == null) {
            return null;
        }
        // 简单掩码：隐藏密码部分
        return connectionString.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }
    
    /**
     * 判断是否应该使用连接池
     */
    private boolean shouldUseConnectionPool(Long dataSourceId) {
        Integer frequency = accessFrequency.get(dataSourceId);
        return frequency != null && frequency >= POOL_THRESHOLD;
    }
    
    /**
     * 更新访问频率
     */
    private void updateAccessFrequency(Long dataSourceId) {
        accessFrequency.merge(dataSourceId, 1, Integer::sum);
    }
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @return 是否连接成功
     */
    public boolean testConnection(DataSource dataSource) {
        return testConnection(dataSource, true);
    }
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @param passwordEncrypted 密码是否已加密
     * @return 是否连接成功
     */
    public boolean testConnection(DataSource dataSource, boolean passwordEncrypted) {
        if (dataSource == null) {
            return false;
        }
        
        try {
            DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
            
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                return testMongoConnection(dataSource, passwordEncrypted);
            } else {
                return testJdbcConnection(dataSource, dbType, passwordEncrypted);
            }
        } catch (Exception e) {
            logger.error("测试数据库连接失败 - 数据源ID: {}", dataSource.getId() != null ? dataSource.getId() : "临时", e);
            return false;
        }
    }
    
    /**
     * 测试 JDBC 连接
     */
    private boolean testJdbcConnection(DataSource dataSource, DatabaseDriverManager.DatabaseType dbType, boolean passwordEncrypted) {
        String password;
        if (passwordEncrypted) {
            // 如果密码已加密，尝试解密
            if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
                try {
                    password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
                } catch (Exception e) {
                    logger.warn("解密密码失败，使用原始密码", e);
                    password = dataSource.getPassword();
                }
            } else {
                password = "";
            }
        } else {
            // 密码未加密，直接使用
            password = dataSource.getPassword() != null ? dataSource.getPassword() : "";
        }
        
        String username = dataSource.getUsername() != null ? dataSource.getUsername() : "";
        String url = driverManager.buildJdbcUrl(dbType, dataSource.getHost(), dataSource.getPort(), dataSource.getDatabase());
        
        try (Connection connection = driverManager.createConnection(dbType, url, username, password)) {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            logger.error("JDBC 连接测试失败 - 类型: {}, 主机: {}, 端口: {}", dbType, dataSource.getHost(), dataSource.getPort(), e);
            return false;
        }
    }
    
    /**
     * 测试 MongoDB 连接
     */
    private boolean testMongoConnection(DataSource dataSource, boolean passwordEncrypted) {
        try {
            MongoDatabase database = getMongoDatabaseForTest(dataSource, passwordEncrypted);
            // 执行一个简单的操作来测试连接
            database.listCollectionNames().first();
            return true;
        } catch (Exception e) {
            logger.error("MongoDB 连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 获取MongoDB数据库（用于测试，支持未加密密码）
     */
    private MongoDatabase getMongoDatabaseForTest(DataSource dataSource, boolean passwordEncrypted) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.MONGODB) {
            throw new IllegalArgumentException("数据源类型不是 MongoDB");
        }
        
        String password;
        if (passwordEncrypted) {
            password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
        } else {
            password = dataSource.getPassword();
        }
        
        String connectionString = buildMongoConnectionString(dataSource, password);
        
        MongoClient client = MongoClients.create(connectionString);
        String databaseName = dataSource.getDatabase();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = "admin"; // 默认数据库
        }
        return client.getDatabase(databaseName);
    }
    
    /**
     * 清除数据源的连接池（当数据源配置更新时调用）
     * @param dataSourceId 数据源ID
     */
    public void clearConnectionPool(Long dataSourceId) {
        javax.sql.DataSource pool = connectionPoolCache.remove(dataSourceId);
        if (pool != null) {
            if (pool instanceof HikariDataSource) {
                ((HikariDataSource) pool).close();
            }
            logger.info("清除数据源连接池 - 数据源ID: {}", dataSourceId);
        }
        
        MongoClient mongoClient = mongoClientCache.remove(dataSourceId);
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("关闭 MongoDB 客户端 - 数据源ID: {}", dataSourceId);
        }
        
        accessFrequency.remove(dataSourceId);
    }
}

