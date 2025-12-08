package com.github.app.dify.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
/**
 * 缓存错误处理器
 * 当Redis缓存操作失败时，记录日志但不抛出异常，确保业务逻辑继续执行
 */
public class CustomCacheErrorHandler implements CacheErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomCacheErrorHandler.class);
    
    @Override
    public void handleCacheGetError(@NotNull RuntimeException exception, Cache cache, @NotNull Object key) {
        logger.warn("缓存获取失败 - Cache: {}, Key: {}, 将直接查询数据库",
                cache.getName(), key, exception);
    }
    
    @Override
    public void handleCachePutError(@NotNull RuntimeException exception, Cache cache, @NotNull Object key, Object value) {
        logger.warn("缓存写入失败 - Cache: {}, Key: {}, 业务逻辑将继续执行",
                cache.getName(), key, exception);
    }
    
    @Override
    public void handleCacheEvictError(@NotNull RuntimeException exception, Cache cache, Object key) {
        logger.warn("缓存清除失败 - Cache: {}, Key: {}, 业务逻辑将继续执行",
                cache.getName(), key, exception);
    }
    
    @Override
    public void handleCacheClearError(@NotNull RuntimeException exception, Cache cache) {
        logger.warn("缓存清空失败 - Cache: {}, 业务逻辑将继续执行",
                cache.getName(), exception);
    }
}