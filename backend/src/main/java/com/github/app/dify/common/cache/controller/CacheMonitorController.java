package com.github.app.dify.common.cache.controller;

import com.github.app.dify.common.cache.CacheMonitorService;
import com.github.app.dify.common.cache.CacheMonitorService.CacheStatistics;
import com.github.app.dify.common.resp.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 缓存监控Controller
 * 
 * 提供缓存管理相关的API接口，用于监控和管理Redis缓存
 */
@RestController
@RequestMapping("/api/cache")
public class CacheMonitorController {

    @Autowired
    private CacheMonitorService cacheMonitorService;

    /**
     * 获取所有缓存的统计信息
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, CacheStatistics>> getAllCacheStatistics() {
        Map<String, CacheStatistics> statistics = cacheMonitorService.getAllCacheStatistics();
        return ApiResponse.success(statistics);
    }

    /**
     * 获取指定缓存的统计信息
     */
    @GetMapping("/statistics/{cacheName}")
    public ApiResponse<CacheStatistics> getCacheStatistics(@PathVariable String cacheName) {
        CacheStatistics statistics = cacheMonitorService.getCacheStatistics(cacheName);
        return ApiResponse.success(statistics);
    }

    /**
     * 获取Redis内存信息
     */
    @GetMapping("/memory")
    public ApiResponse<Map<String, Object>> getRedisMemoryInfo() {
        Map<String, Object> memoryInfo = cacheMonitorService.getRedisMemoryInfo();
        return ApiResponse.success(memoryInfo);
    }

    /**
     * 获取缓存键的详细信息
     */
    @GetMapping("/keys/{cacheName}")
    public ApiResponse<List<Map<String, Object>>> getCacheKeysDetails(
            @PathVariable String cacheName,
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> details = cacheMonitorService.getCacheKeysDetails(cacheName, limit);
        return ApiResponse.success(details);
    }

    /**
     * 搜索缓存键
     */
    @GetMapping("/keys/search")
    public ApiResponse<Set<String>> searchKeys(@RequestParam String pattern) {
        Set<String> keys = cacheMonitorService.searchKeys(pattern);
        return ApiResponse.success(keys);
    }

    /**
     * 清理指定缓存
     */
    @DeleteMapping("/clear/{cacheName}")
    public ApiResponse<Long> clearCache(@PathVariable String cacheName) {
        Long count = cacheMonitorService.clearCache(cacheName);
        return ApiResponse.success("清理成功，删除了 " + count + " 个键", count);
    }

    /**
     * 清理所有缓存
     */
    @DeleteMapping("/clear/all")
    public ApiResponse<Long> clearAllCache() {
        Long count = cacheMonitorService.clearAllCache();
        return ApiResponse.success("清理成功，删除了 " + count + " 个键", count);
    }

    /**
     * 删除指定键
     */
    @DeleteMapping("/key/{key}")
    public ApiResponse<Boolean> deleteKey(@PathVariable String key) {
        Boolean result = cacheMonitorService.deleteKey(key);
        return ApiResponse.success(result ? "删除成功" : "删除失败", result);
    }

    /**
     * 设置指定键的TTL
     */
    @PutMapping("/key/{key}/ttl")
    public ApiResponse<Boolean> setKeyTTL(
            @PathVariable String key,
            @RequestParam long timeout,
            @RequestParam(defaultValue = "SECONDS") TimeUnit unit) {
        Boolean result = cacheMonitorService.setKeyTTL(key, timeout, unit);
        return ApiResponse.success(result ? "设置成功" : "设置失败", result);
    }

    /**
     * 获取指定键的TTL
     */
    @GetMapping("/key/{key}/ttl")
    public ApiResponse<Long> getKeyTTL(@PathVariable String key) {
        Long ttl = cacheMonitorService.getKeyTTL(key);
        return ApiResponse.success(ttl);
    }
}
