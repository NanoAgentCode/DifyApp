package com.github.app.dify.datasource.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.service.DatabaseConnectionService;
import com.github.app.dify.datasource.util.DatabaseDriverManager;
import com.github.app.dify.auth.util.PasswordEncryptionUtil;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
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
public class DatabaseConnectionServiceImpl implements DatabaseConnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionServiceImpl.class);
    
    @Autowired
    private PasswordEncryptionUtil passwordEncryptionUtil;
    
    @Autowired
    private DatabaseDriverManager driverManager;
    
    // 连接池缓存（常用数据源）
    private final Map<Long, javax.sql.DataSource> connectionPoolCache = new ConcurrentHashMap<>();
    
    // MongoDB 客户端缓存
    private final Map<Long, MongoClient> mongoClientCache = new ConcurrentHashMap<>();
    
    // Neo4j 驱动缓存
    private final Map<Long, Driver> neo4jDriverCache = new ConcurrentHashMap<>();
    
    // Elasticsearch 客户端缓存
    private final Map<Long, ElasticsearchClient> elasticsearchClientCache = new ConcurrentHashMap<>();
    
    // 数据源访问频率统计（用于决定是否使用连接池）
    private final Map<Long, Integer> accessFrequency = new ConcurrentHashMap<>();
    
    // 连接池阈值：访问次数超过此值的数据源使用连接池
    private static final int POOL_THRESHOLD = 10;
    
    /**
     * 获取数据库连接
     * @param dataSource 数据源配置
     * @return 数据库连接
     */
    @Override
    public Connection getConnection(DataSource dataSource) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        
        if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
            throw new UnsupportedOperationException("MongoDB 不支持 JDBC 连接，请使用 getMongoDatabase 方法");
        }
        
        if (dbType == DatabaseDriverManager.DatabaseType.NEO4J) {
            throw new UnsupportedOperationException("Neo4j 不支持 JDBC 连接，请使用 getNeo4jSession 方法");
        }
        
        if (dbType == DatabaseDriverManager.DatabaseType.ELASTICSEARCH) {
            throw new UnsupportedOperationException("Elasticsearch 不支持 JDBC 连接，请使用 getElasticsearchClient 方法");
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
    @Override
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
     * 获取 Neo4j 驱动
     * @param dataSource 数据源配置
     * @return Neo4j 驱动
     */
    @Override
    public Driver getNeo4jDriver(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.NEO4J) {
            throw new IllegalArgumentException("数据源类型不是 Neo4j");
        }
        
        // 更新访问频率
        updateAccessFrequency(dataSource.getId());
        
        // 获取或创建 Neo4j 驱动
        return neo4jDriverCache.computeIfAbsent(dataSource.getId(), id -> {
            String password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
            String uri = driverManager.buildJdbcUrl(dbType, dataSource.getHost(), dataSource.getPort(), dataSource.getDatabase());
            
            String username = dataSource.getUsername() != null ? dataSource.getUsername() : "";
            password = password != null ? password : "";
            
            // 配置连接超时和连接池
            Config config = Config.builder()
                    .withConnectionTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .withMaxConnectionLifetime(30, java.util.concurrent.TimeUnit.MINUTES)
                    .withMaxConnectionPoolSize(50)
                    .build();
            
            logger.info("创建 Neo4j 驱动 - 数据源ID: {}, URI: {}", id, maskConnectionString(uri));
            return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
        });
    }
    
    /**
     * 获取 Neo4j 会话
     * @param dataSource 数据源配置
     * @return Neo4j 会话
     */
    @Override
    public Session getNeo4jSession(DataSource dataSource) {
        Driver driver = getNeo4jDriver(dataSource);
        String database = dataSource.getDatabase();
        if (database != null && !database.isEmpty()) {
            return driver.session(org.neo4j.driver.SessionConfig.forDatabase(database));
        }
        return driver.session();
    }
    
    /**
     * 获取 Elasticsearch 客户端
     * @param dataSource 数据源配置
     * @return Elasticsearch 客户端
     */
    @Override
    public ElasticsearchClient getElasticsearchClient(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.ELASTICSEARCH) {
            throw new IllegalArgumentException("数据源类型不是 Elasticsearch");
        }
        
        // 更新访问频率
        updateAccessFrequency(dataSource.getId());
        
        // 获取或创建 Elasticsearch 客户端
        return elasticsearchClientCache.computeIfAbsent(dataSource.getId(), id -> {
            String password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
            String username = dataSource.getUsername() != null ? dataSource.getUsername() : "";
            password = password != null ? password : "";
            
            String host = dataSource.getHost();
            int port = dataSource.getPort() != null ? dataSource.getPort() : 9200;
            
            logger.info("创建 Elasticsearch 客户端 - 数据源ID: {}, 主机: {}, 端口: {}", id, host, port);
            
            try {
                // 创建 RestClient
                RestClientBuilder restClientBuilder = RestClient.builder(
                    new HttpHost(host, port, "http")
                );
                
                // 配置超时时间（默认30秒连接超时，60秒socket超时）
                final int connectTimeout = 30000; // 连接超时：30秒
                final int socketTimeout = 60000;  // Socket超时：60秒
                final int connectionRequestTimeout = 5000; // 从连接池获取连接超时：5秒
                
                restClientBuilder.setRequestConfigCallback(requestConfigBuilder -> {
                    return requestConfigBuilder
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(socketTimeout)
                        .setConnectionRequestTimeout(connectionRequestTimeout);
                });
                
                // 配置认证和HTTP客户端
                if (username != null && !username.trim().isEmpty() && password != null && !password.trim().isEmpty()) {
                    final String finalUsername = username;
                    final String finalPassword = password;
                    
                    restClientBuilder.setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) -> {
                        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                            AuthScope.ANY,
                            new UsernamePasswordCredentials(finalUsername, finalPassword)
                        );
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        
                        // 配置连接池参数
                        httpClientBuilder.setMaxConnTotal(100); // 最大连接数
                        httpClientBuilder.setMaxConnPerRoute(10); // 每个路由的最大连接数
                        
                        logger.debug("配置 Elasticsearch Basic Auth 认证 - 用户名: {}", finalUsername);
                        return httpClientBuilder;
                    });
                } else {
                    // 即使没有认证，也需要配置连接池参数
                    restClientBuilder.setHttpClientConfigCallback((HttpAsyncClientBuilder httpClientBuilder) -> {
                        httpClientBuilder.setMaxConnTotal(100); // 最大连接数
                        httpClientBuilder.setMaxConnPerRoute(10); // 每个路由的最大连接数
                        return httpClientBuilder;
                    });
                }
                
                RestClient restClient = restClientBuilder.build();
                
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                
                RestClientTransport transport = new RestClientTransport(
                    restClient,
                    new JacksonJsonpMapper(objectMapper)
                );
                
                return new ElasticsearchClient(transport);
            } catch (Exception e) {
                logger.error("创建 Elasticsearch 客户端失败", e);
                throw new BusinessException("创建 Elasticsearch 客户端失败", ErrorCode.ELASTICSEARCH_CONNECTION_ERROR, e);
            }
        });
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
        
        // Oracle 需要使用 "SELECT 1 FROM DUAL"，其他数据库使用 "SELECT 1"
        if (dbType == DatabaseDriverManager.DatabaseType.ORACLE) {
            config.setConnectionTestQuery("SELECT 1 FROM DUAL");
        } else {
            config.setConnectionTestQuery("SELECT 1");
        }
        
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
        accessFrequency.merge(dataSourceId, 1, (a, b) -> a + b);
    }
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @return 是否连接成功
     */
    @Override
    public boolean testConnection(DataSource dataSource) {
        return testConnection(dataSource, true);
    }
    
    /**
     * 测试数据库连接
     * @param dataSource 数据源配置
     * @param passwordEncrypted 密码是否已加密
     * @return 是否连接成功
     */
    @Override
    public boolean testConnection(DataSource dataSource, boolean passwordEncrypted) {
        if (dataSource == null) {
            return false;
        }
        
        try {
            DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
            
            if (dbType == DatabaseDriverManager.DatabaseType.MONGODB) {
                return testMongoConnection(dataSource, passwordEncrypted);
            } else if (dbType == DatabaseDriverManager.DatabaseType.NEO4J) {
                return testNeo4jConnection(dataSource, passwordEncrypted);
            } else if (dbType == DatabaseDriverManager.DatabaseType.ELASTICSEARCH) {
                return testElasticsearchConnection(dataSource, passwordEncrypted);
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
     * 测试 Neo4j 连接
     */
    private boolean testNeo4jConnection(DataSource dataSource, boolean passwordEncrypted) {
        Driver driver = null;
        try {
            driver = getNeo4jDriverForTest(dataSource, passwordEncrypted);
            try (Session session = driver.session()) {
                // 执行一个简单的查询来测试连接
                session.run("RETURN 1 AS test").consume();
                logger.info("Neo4j 连接测试成功: {}:{}", dataSource.getHost(), dataSource.getPort());
                return true;
            }
        } catch (ServiceUnavailableException e) {
            String errorMsg = String.format(
                "无法连接到 Neo4j 服务器 %s:%d。请检查：\n" +
                "1. Neo4j 服务是否正在运行\n" +
                "2. 主机地址和端口是否正确（默认 Bolt 端口为 7687）\n" +
                "3. 网络连接是否正常\n" +
                "4. 防火墙是否允许该端口",
                dataSource.getHost(), dataSource.getPort()
            );
            logger.error("Neo4j 连接测试失败: {}", errorMsg, e);
            throw new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR, e);
        } catch (Exception e) {
            String errorMsg = String.format("Neo4j 连接测试失败: %s", e.getMessage());
            logger.error("Neo4j 连接测试失败: {}:{}", dataSource.getHost(), dataSource.getPort(), e);
            throw new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR, e);
        } finally {
            if (driver != null) {
                try {
                    driver.close();
                } catch (Exception e) {
                    logger.warn("关闭 Neo4j 驱动失败", e);
                }
            }
        }
    }
    
    /**
     * 测试 Elasticsearch 连接
     */
    private boolean testElasticsearchConnection(DataSource dataSource, boolean passwordEncrypted) {
        try {
            ElasticsearchClient client = getElasticsearchClientForTest(dataSource, passwordEncrypted);
            // 执行一个简单的 ping 操作来测试连接
            client.ping();
            return true;
        } catch (Exception e) {
            logger.error("Elasticsearch 连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 获取 Neo4j 驱动（用于测试，支持未加密密码）
     */
    private Driver getNeo4jDriverForTest(DataSource dataSource, boolean passwordEncrypted) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.NEO4J) {
            throw new IllegalArgumentException("数据源类型不是 Neo4j");
        }
        
        String password;
        if (passwordEncrypted) {
            password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
        } else {
            password = dataSource.getPassword();
        }
        
        String uri = driverManager.buildJdbcUrl(dbType, dataSource.getHost(), dataSource.getPort(), dataSource.getDatabase());
        String username = dataSource.getUsername() != null ? dataSource.getUsername() : "";
        password = password != null ? password : "";
        
        // 配置连接超时（10秒）
        Config config = Config.builder()
                .withConnectionTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxConnectionLifetime(30, java.util.concurrent.TimeUnit.MINUTES)
                .withMaxConnectionPoolSize(50)
                .build();
        
        logger.debug("创建 Neo4j 驱动: uri={}, username={}", uri, username);
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password), config);
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
     * 获取 Elasticsearch 客户端（用于测试，支持未加密密码）
     */
    private ElasticsearchClient getElasticsearchClientForTest(DataSource dataSource, boolean passwordEncrypted) {
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        
        DatabaseDriverManager.DatabaseType dbType = DatabaseDriverManager.DatabaseType.fromString(dataSource.getType());
        if (dbType != DatabaseDriverManager.DatabaseType.ELASTICSEARCH) {
            throw new IllegalArgumentException("数据源类型不是 Elasticsearch");
        }
        
        String password;
        if (passwordEncrypted) {
            password = passwordEncryptionUtil.decrypt(dataSource.getPassword());
        } else {
            password = dataSource.getPassword();
        }
        
        String username = dataSource.getUsername() != null ? dataSource.getUsername() : "";
        password = password != null ? password : "";
        
        String host = dataSource.getHost();
        int port = dataSource.getPort() != null ? dataSource.getPort() : 9200;
        
            try {
                // 创建 RestClient
                RestClientBuilder restClientBuilder = RestClient.builder(
                    new HttpHost(host, port, "http")
                );
                
                // 配置认证
                if (username != null && !username.trim().isEmpty() && password != null && !password.trim().isEmpty()) {
                    final String finalUsername = username;
                    final String finalPassword = password;
                    
                    restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
                        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                            AuthScope.ANY,
                            new UsernamePasswordCredentials(finalUsername, finalPassword)
                        );
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        return httpClientBuilder;
                    });
                }
                
                RestClient restClient = restClientBuilder.build();
                
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(new JavaTimeModule());
                
                // 创建传输层
                RestClientTransport transport = new RestClientTransport(
                    restClient,
                    new JacksonJsonpMapper(objectMapper)
                );
                
                return new ElasticsearchClient(transport);
        } catch (Exception e) {
            logger.error("创建 Elasticsearch 测试客户端失败", e);
            throw new BusinessException("创建 Elasticsearch 测试客户端失败", ErrorCode.ELASTICSEARCH_CONNECTION_ERROR, e);
        }
    }
    
    /**
     * 清除数据源的连接池（当数据源配置更新时调用）
     * @param dataSourceId 数据源ID
     */
    @Override
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
        
        Driver neo4jDriver = neo4jDriverCache.remove(dataSourceId);
        if (neo4jDriver != null) {
            neo4jDriver.close();
            logger.info("关闭 Neo4j 驱动 - 数据源ID: {}", dataSourceId);
        }
        
        ElasticsearchClient elasticsearchClient = elasticsearchClientCache.remove(dataSourceId);
        if (elasticsearchClient != null) {
            try {
                elasticsearchClient._transport().close();
                logger.info("关闭 Elasticsearch 客户端 - 数据源ID: {}", dataSourceId);
            } catch (Exception e) {
                logger.warn("关闭 Elasticsearch 客户端失败 - 数据源ID: {}", dataSourceId, e);
            }
        }
        
        accessFrequency.remove(dataSourceId);
    }
}

