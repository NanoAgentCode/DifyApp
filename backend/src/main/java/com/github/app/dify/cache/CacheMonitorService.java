package com.github.app.dify.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存监控服务
 * 
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
    public static class CacheStatistics {
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

        // Getters and Setters
        public String getCacheName() { return cacheName; }
        public void setCacheName(String cacheName) { this.cacheName = cacheName; }
        public Long getKeyCount() { return keyCount; }
        public void setKeyCount(Long keyCount) { this.keyCount = keyCount; }
        public Long getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(Long memoryUsage) { this.memoryUsage = memoryUsage; }
        public Long getTtl() { return ttl; }
        public void setTtl(Long ttl) { this.ttl = ttl; }
        public String getMemoryUsageStr() { return memoryUsageStr; }
        public void setMemoryUsageStr(String memoryUsageStr) { this.memoryUsageStr = memoryUsageStr; }

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
        Long keyCount = keys != null ? (long) keys.size() : 0L;

        // 计算内存使用（近似值）
        Long memoryUsage = 0L;
        if (keys != null && !keys.isEmpty()) {
            // 采样计算（取前10个键的平均值）
            int sampleSize = Math.min(keys.size(), 10);
            List<String> sampleKeys = new ArrayList<>(keys).subList(0, sampleSize);
            Long sampleMemory = 0L;
            
            for (String key : sampleKeys) {
                Long memory = redisTemplate.execute(connection -> {
                    return connection.memoryUsage(key.getBytes());
                });
                if (memory != null) {
                    sampleMemory += memory;
                }
            }
            
            // 估算总内存使用
            memoryUsage = (sampleMemory / sampleSize) * keyCount;
        }

        // 获取TTL（取第一个键的TTL作为参考）
        Long ttl = 0L;
        if (keys != null && !keys.isEmpty()) {
            ttl = redisTemplate.getExpire(keys.iterator().next());
        }

        return new CacheStatistics(cacheName, keyCount, memoryUsage, ttl);
    }

    /**
     * 清理指定缓存
     */
    public Long clearCache(String cacheName) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        if (keys != null && !keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    /**
     * 清理所有缓存
     */
    public Long clearAllCache() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            return redisTemplate.delete(keys);
        }
        return 0L;
    }

    /**
     * 获取Redis总内存使用情况
     */
    public Map<String, Object> getRedisMemoryInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // 获取Redis内存信息
        Properties memoryInfo = redisTemplate.execute(connection -> {
            return connection.info("memory");
        });
        
        if (memoryInfo != null) {
            info.put("used_memory", memoryInfo.getProperty("used_memory"));
            info.put("used_memory_human", memoryInfo.getProperty("used_memory_human"));
            info.put("used_memory_peak", memoryInfo.getProperty("used_memory_peak"));
            info.put("used_memory_peak_human", memoryInfo.getProperty("used_memory_peak_human"));
            info.put("maxmemory", memoryInfo.getProperty("maxmemory"));
            info.put("maxmemory_human", memoryInfo.getProperty("maxmemory_human"));
        }
        
        // 获取数据库信息
        Properties dbInfo = redisTemplate.execute(connection -> {
            return connection.info("keyspace");
        });
        
        if (dbInfo != null) {
            info.put("keyspace", dbInfo);
        }
        
        return info;
    }

    /**
     * 获取缓存键的详细信息（用于调试）
     */
    public List<Map<String, Object>> getCacheKeysDetails(String cacheName, int limit) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        List<Map<String, Object>> details = new ArrayList<>();
        
        if (keys != null) {
            int count = 0;
            for (String key : keys) {
                if (count >= limit) break;
                
                Map<String, Object> detail = new HashMap<>();
                detail.put("key", key);
                detail.put("ttl", redisTemplate.getExpire(key, TimeUnit.SECONDS));
                
                Long memory = redisTemplate.execute(connection -> {
                    return connection.memoryUsage(key.getBytes());
                });
                detail.put("memory", memory);
                
                details.add(detail);
                count++;
            }
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
}
