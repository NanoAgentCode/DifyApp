package com.github.app.dify.appaichat.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
/**
 * MCP模块配置类
 * 统一管理所有MCP服务的配置
 */
@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpConfig {
    
    /**
     * 浏览器搜索服务配置
     */
    private BrowserSearch browserSearch = new BrowserSearch();
    
    /**
     * 地理位置服务配置
     */
    private Location location = new Location();
    
    /**
     * 时间服务配置
     */
    private Time time = new Time();
    
    /**
     * 实时信息检测配置
     */
    private RealtimeInfoDetector realtimeInfoDetector = new RealtimeInfoDetector();
    
    public BrowserSearch getBrowserSearch() {
        return browserSearch;
    }
    
    public void setBrowserSearch(BrowserSearch browserSearch) {
        this.browserSearch = browserSearch;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public void setLocation(Location location) {
        this.location = location;
    }
    
    public Time getTime() {
        return time;
    }
    
    public void setTime(Time time) {
        this.time = time;
    }
    
    public RealtimeInfoDetector getRealtimeInfoDetector() {
        return realtimeInfoDetector;
    }
    
    public void setRealtimeInfoDetector(RealtimeInfoDetector realtimeInfoDetector) {
        this.realtimeInfoDetector = realtimeInfoDetector;
    }
    
    /**
     * 浏览器搜索服务配置
     */
    public static class BrowserSearch {
        /**
         * SearX-NG服务地址
         */
        private String searxngBaseUrl = "http://localhost:10086";
        
        /**
         * 请求超时时间（秒）
         */
        private int timeout = 10;
        
        /**
         * 默认最大搜索结果数
         */
        private int defaultMaxResults = 5;
        
        /**
         * 是否启用查询优化
         */
        private boolean enableQueryOptimization = true;
        
        /**
         * 默认搜索引擎（多个用逗号分隔，例如：google,bing,duckduckgo）
         * 如果为空，则使用SearX-NG的默认配置
         */
        private String defaultEngines = "";
        
        public String getSearxngBaseUrl() {
            return searxngBaseUrl;
        }
        
        public void setSearxngBaseUrl(String searxngBaseUrl) {
            this.searxngBaseUrl = searxngBaseUrl;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public int getDefaultMaxResults() {
            return defaultMaxResults;
        }
        
        public void setDefaultMaxResults(int defaultMaxResults) {
            this.defaultMaxResults = defaultMaxResults;
        }
        
        public boolean isEnableQueryOptimization() {
            return enableQueryOptimization;
        }
        
        public void setEnableQueryOptimization(boolean enableQueryOptimization) {
            this.enableQueryOptimization = enableQueryOptimization;
        }
        
        public String getDefaultEngines() {
            return defaultEngines;
        }
        
        public void setDefaultEngines(String defaultEngines) {
            this.defaultEngines = defaultEngines;
        }
    }
    
    /**
     * 地理位置服务配置
     */
    public static class Location {
        /**
         * 地理位置信息缓存时间（秒）
         */
        private int cacheSeconds = 3600; // 1小时
        
        /**
         * 请求超时时间（秒）
         */
        private int timeout = 10;
        
        /**
         * 是否启用地理位置服务
         */
        private boolean enabled = true;
        
        public int getCacheSeconds() {
            return cacheSeconds;
        }
        
        public void setCacheSeconds(int cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    /**
     * 时间服务配置
     */
    public static class Time {
        /**
         * 默认时区
         */
        private String defaultTimeZone = "Asia/Shanghai";
        
        /**
         * 时间信息缓存时间（秒）
         */
        private int cacheSeconds = 1; // 1秒缓存，避免频繁获取
        
        public String getDefaultTimeZone() {
            return defaultTimeZone;
        }
        
        public void setDefaultTimeZone(String defaultTimeZone) {
            this.defaultTimeZone = defaultTimeZone;
        }
        
        public int getCacheSeconds() {
            return cacheSeconds;
        }
        
        public void setCacheSeconds(int cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }
    }
    
    /**
     * 实时信息检测配置
     */
    public static class RealtimeInfoDetector {
        /**
         * 检测置信度阈值（0.0-1.0）
         */
        private double confidenceThreshold = 0.5;
        
        /**
         * 是否启用智能检测
         */
        private boolean enabled = true;
        
        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }
        
        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}