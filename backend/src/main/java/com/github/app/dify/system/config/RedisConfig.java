package com.github.app.dify.system.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import io.lettuce.core.api.StatefulConnection;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
/**
 * Redis配置类
 */
@Setter
@Getter
@Configuration
@EnableCaching
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisConfig implements CachingConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);
    
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private int timeout = 3000;
    
    // 连接池配置（从application.yml读取）
    private Lettuce lettuce;
    
    @Getter
    @Setter
    public static class Lettuce {
        private Pool pool;
    }
    
    @Getter
    @Setter
    public static class Pool {
        private int maxActive = 20;
        private int maxIdle = 10;
        private int minIdle = 5;
        private long maxWait = 3000;
    }
    
    /**
     * Redis连接工厂
     * 如果Redis连接失败，应用仍能正常启动，但缓存功能将不可用
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(host);
            config.setPort(port);
            config.setDatabase(database);
            if (password != null && !password.isEmpty()) {
                config.setPassword(password);
            }
            
            // 配置连接池（性能优化）
            GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
            if (lettuce != null && lettuce.getPool() != null) {
                Pool pool = lettuce.getPool();
                poolConfig.setMaxTotal(pool.getMaxActive());
                poolConfig.setMaxIdle(pool.getMaxIdle());
                poolConfig.setMinIdle(pool.getMinIdle());
                poolConfig.setMaxWait(Duration.ofMillis(pool.getMaxWait()));
            } else {
                // 使用默认值
                poolConfig.setMaxTotal(20);
                poolConfig.setMaxIdle(10);
                poolConfig.setMinIdle(5);
                poolConfig.setMaxWait(Duration.ofMillis(3000));
            }
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofMillis(30000));
            
            LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                    .poolConfig(poolConfig)
                    .commandTimeout(Duration.ofMillis(timeout))
                    .build();
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
            
            logger.info("Redis连接配置 - Host: {}, Port: {}, Database: {}, Timeout: {}ms, Pool: {}/{}/{}", 
                    host, port, database, timeout, 
                    poolConfig.getMaxTotal(), poolConfig.getMaxIdle(), poolConfig.getMinIdle());
            return factory;
        } catch (Exception e) {
            logger.error("Redis连接工厂初始化失败，应用将继续启动但缓存功能不可用", e);
            // 返回一个工厂实例，但连接可能失败，后续会在CacheManager中处理
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration());
        }
    }
    
    /**
     * RedisTemplate配置
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 使用Jackson2JsonRedisSerializer来序列化和反序列化redis的value值
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 配置ObjectMapper以支持多态类型
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(om, Object.class);
        
        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        
        // key采用String的序列化方式
        template.setKeySerializer(stringRedisSerializer);
        // hash的key也采用String的序列化方式
        template.setHashKeySerializer(stringRedisSerializer);
        // value序列化方式采用jackson
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // hash的value序列化方式采用jackson
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        
        template.afterPropertiesSet();
        logger.info("RedisTemplate配置完成");
        return template;
    }
    
    /**
     * 缓存管理器配置
     * 如果Redis不可用，将使用NoOpCacheManager（不缓存），确保系统仍能正常工作
     * 为不同类型的缓存设置不同的过期时间，优化缓存策略
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        try {
            // 测试Redis连接（延迟测试，避免启动时阻塞）
            try {
                connectionFactory.getConnection().ping();
                logger.info("Redis连接测试成功");
            } catch (Exception e) {
                logger.warn("Redis连接测试失败，将在运行时降级: {}", e.getMessage());
                // 不立即返回NoOpCacheManager，让Spring Cache在运行时处理
            }
            
            // 创建Jackson序列化器，支持多态类型
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
            );
            
            // 使用 GenericJackson2JsonRedisSerializer 以支持类型信息
            GenericJackson2JsonRedisSerializer jsonRedisSerializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);
            
            // 配置序列化（解决乱码和类型转换的问题）
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(1)) // 默认缓存过期时间1小时
                    .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer))
                    .disableCachingNullValues(); // 不缓存空值
            
            // 为不同类型的缓存配置不同的TTL
            Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

            // ==================== 用户相关缓存 ====================
            // 用户信息：24小时（用户信息变化不频繁）
            cacheConfigurations.put("user", defaultConfig
                .entryTtl(Duration.ofHours(24)));

            // ==================== 模型相关缓存 ====================
            // 模型配置：12小时（配置变化中等频率）
            cacheConfigurations.put("modelConfig", defaultConfig
                .entryTtl(Duration.ofHours(12)));
            cacheConfigurations.put("qaModel", defaultConfig
                .entryTtl(Duration.ofHours(12)));
            cacheConfigurations.put("embeddingModel", defaultConfig
                .entryTtl(Duration.ofHours(12)));

            // ==================== 向量化缓存 ====================
            // 向量化结果：7天（同一查询的embedding可以长期复用）
            cacheConfigurations.put("embedding", defaultConfig
                .entryTtl(Duration.ofDays(7)));

            // ==================== RAG检索缓存 ====================
            // RAG检索结果：1小时（知识库内容可能更新）
            cacheConfigurations.put("rag", defaultConfig
                .entryTtl(Duration.ofHours(1)));

            // ==================== 应用缓存 ====================
            // AI应用配置：12小时（配置变化中等频率）
            cacheConfigurations.put("aiApp", defaultConfig
                .entryTtl(Duration.ofHours(12)));

            // ==================== 知识库缓存 ====================
            // 知识库配置：6小时（知识库可能更新）
            cacheConfigurations.put("knowledgeBase", defaultConfig
                .entryTtl(Duration.ofHours(6)));

            // ==================== 向量数据库缓存 ====================
            // 向量数据库配置：24小时（配置很少变化）
            cacheConfigurations.put("vectorDatabase", defaultConfig
                .entryTtl(Duration.ofHours(24)));

            // ==================== 统计缓存 ====================
            // 统计数据：5分钟（统计数据需要实时性）
            cacheConfigurations.put("statistics", defaultConfig
                .entryTtl(Duration.ofMinutes(5)));
            cacheConfigurations.put("statistics:popular", defaultConfig
                .entryTtl(Duration.ofMinutes(5)));
            cacheConfigurations.put("statistics:active", defaultConfig
                .entryTtl(Duration.ofMinutes(5)));

            // ==================== 系统配置缓存 ====================
            // 系统配置：24小时（配置很少变化）
            cacheConfigurations.put("systemConfig", defaultConfig
                .entryTtl(Duration.ofHours(24)));
            
            logger.info("Redis缓存管理器初始化完成，已配置{}个缓存类型的TTL", cacheConfigurations.size() + 1);
            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(defaultConfig)
                    .withInitialCacheConfigurations(cacheConfigurations)
                    .transactionAware()
                    .build();
        } catch (Exception e) {
            logger.error("==========================================");
            logger.error("Redis缓存管理器初始化失败，缓存功能将被禁用！");
            logger.error("错误信息: {}", e.getMessage());
            logger.error("==========================================");
            logger.warn("应用将继续启动，但缓存功能不可用，所有查询将直接访问数据库");
            logger.warn("请检查Redis服务是否正在运行，配置是否正确");
            // 返回NoOpCacheManager，不进行任何缓存操作，确保系统正常运行
            return new NoOpCacheManager();
        }
    }

    /**
     * 缓存错误处理器
     * 当缓存操作失败时，记录日志但不抛出异常，确保业务逻辑继续执行
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }
}
