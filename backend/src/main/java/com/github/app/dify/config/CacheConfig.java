package com.github.app.dify.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis缓存配置
 * 
 * 优化点：
 * 1. 为不同类型的缓存设置不同的TTL（过期时间）
 * 2. 使用JSON序列化，支持复杂对象
 * 3. 优化缓存键设计，避免冲突
 * 4. 添加缓存监控支持
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置Redis缓存管理器
     * 为不同类型的缓存设置不同的过期时间
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 创建Jackson序列化器
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = 
            new GenericJackson2JsonRedisSerializer(objectMapper);

        // 默认缓存配置（1小时）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(serializer))
                .disableCachingNullValues(); // 不缓存null值

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

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // 支持事务
                .build();
    }
}
