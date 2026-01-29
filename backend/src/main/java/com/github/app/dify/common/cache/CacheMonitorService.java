package com.github.app.dify.common.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存监控服务
 * <p>
 * 功能：
 * 1. 查看缓存命中率
 * 2. 查看缓存键数量
 * 3. 查看缓存大小
 * 4. 清理指定缓存
 * 5. 获取缓存统计信息
 */
@Service
public class CacheMonitorService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存统计信息
     */
    @Setter
    @Getter
    public static class CacheStatistics {
        // Getters and Setters
        private String cacheName;
        private Long keyCount;          // 键数量
        private Long memoryUsage;       // 内存使用（字节）
        private Long ttl;              // TTL（秒）
        private String memoryUsageStr;  // 内存使用（可读格式）

        public CacheStatistics(String cacheName, Long keyCount, Long memoryUsage, Long ttl) {
            this.cacheName = cacheName;
            this.keyCount = keyCount;
            this.memoryUsage = memoryUsage;
            this.ttl = ttl;
            this.memoryUsageStr = formatBytes(memoryUsage);
        }

        /**
         * 格式化字节大小
         */
        private String formatBytes(Long bytes) {
            if (bytes == null) return "0 B";
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            char pre = "KMGTPE".charAt(exp - 1);
            return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
        }
    }

    /**
     * 获取所有缓存的统计信息
     */
    public Map<String, CacheStatistics> getAllCacheStatistics() {
        Map<String, CacheStatistics> statistics = new HashMap<>();
        
        // 定义所有缓存名称
        String[] cacheNames = {
            "user",
            "modelConfig",
            "qaModel",
            "embeddingModel",
            "embedding",
            "rag",
            "aiApp",
            "knowledgeBase",
            "vectorDatabase",
            "statistics",
            "statistics:popular",
            "statistics:active",
            "systemConfig"
        };

        for (String cacheName : cacheNames) {
            CacheStatistics stats = getCacheStatistics(cacheName);
            statistics.put(cacheName, stats);
        }

        return statistics;
    }

    /**
     * 获取指定缓存的统计信息
     */
    public CacheStatistics getCacheStatistics(String cacheName) {
        // 获取所有匹配的键
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        long keyCount = keys.size();

        // 简化内存使用计算（移除MEMORY USAGE命令，使用键数量估算）
        long memoryUsage = 0L;
        if (!keys.isEmpty()) {
            // 使用简化的估算方法：每个键平均100字节
            memoryUsage = keyCount * 100L;
        }

        // 获取TTL（取第一个键的TTL作为参考）
        Long ttl = 0L;
        if (!keys.isEmpty()) {
            ttl = redisTemplate.getExpire(keys.iterator().next());
        }

        return new CacheStatistics(cacheName, keyCount, memoryUsage, ttl);
    }

    /**
     * 清理指定缓存
     */
    public Long clearCache(String cacheName) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        if (!keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    /**
     * 清理所有缓存
     */
    public Long clearAllCache() {
        Set<String> keys = redisTemplate.keys("*");
        if (!keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    /**
     * 获取Redis总内存使用情况
     */
    public Map<String, Object> getRedisMemoryInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // 使用RedisTemplate直接执行命令获取内存信息
        try {
            Properties memoryInfo = redisTemplate.execute((RedisCallback<Properties>) connection -> 
                connection.serverCommands().info("memory")
            );
            
            if (memoryInfo != null) {
                info.put("used_memory", memoryInfo.getProperty("used_memory"));
                info.put("used_memory_human", memoryInfo.getProperty("used_memory_human"));
                info.put("used_memory_peak", memoryInfo.getProperty("used_memory_peak"));
                info.put("used_memory_peak_human", memoryInfo.getProperty("used_memory_peak_human"));
                info.put("maxmemory", memoryInfo.getProperty("maxmemory"));
                info.put("maxmemory_human", memoryInfo.getProperty("maxmemory_human"));
            }
        } catch (Exception e) {
            // 如果获取失败，返回基本信息
            info.put("error", "无法获取Redis内存信息: " + e.getMessage());
        }
        
        return info;
    }

    /**
     * 获取缓存键的详细信息（用于调试）
     */
    public List<Map<String, Object>> getCacheKeysDetails(String cacheName, int limit) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        List<Map<String, Object>> details = new ArrayList<>();

        int count = 0;
        for (String key : keys) {
            if (count >= limit) break;

            Map<String, Object> detail = new HashMap<>();
            detail.put("key", key);
            detail.put("ttl", redisTemplate.getExpire(key, TimeUnit.SECONDS));

            // 简化内存估算
            detail.put("memory", key.getBytes().length);

            details.add(detail);
            count++;
        }

        return details;
    }

    /**
     * 搜索缓存键
     */
    public Set<String> searchKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * 删除指定键
     */
    public Boolean deleteKey(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 设置指定键的TTL
     */
    public Boolean setKeyTTL(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 获取指定键的TTL
     */
    public Long getKeyTTL(String key) {
        return redisTemplate.getExpire(key);
    }

    /**
     * 使指定知识库的 RAG 检索缓存失效（文档新增/更新/删除/重索引后调用，避免返回旧结果）
     * 兼容 Spring Redis 两种常见 key 格式：rag::rag:kb:{id}:* 与 rag:rag:kb:{id}:*
     */
    public void evictRagCacheForKnowledgeBase(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return;
        }
        for (String pattern : new String[]{"rag::rag:kb:" + knowledgeBaseId + ":*", "rag:rag:kb:" + knowledgeBaseId + ":*"}) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}
