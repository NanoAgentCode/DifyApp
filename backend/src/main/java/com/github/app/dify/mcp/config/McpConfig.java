package com.github.app.dify.mcp.config;

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
         * SearX-NG服务地址（降级方案）
         */
        private String searxngBaseUrl = "http://localhost:10086";
        
        /**
         * Tavily API密钥（推荐：专为AI优化的搜索API）
         * 官网：https://tavily.com
         */
        private String tavilyApiKey = "";
        
        /**
         * SerpAPI密钥（支持Google、Bing等）
         * 官网：https://serpapi.com
         */
        private String serpApiKey = "";
        
        /**
         * SerpAPI使用的搜索引擎（google, bing, yahoo等）
         */
        private String serpEngine = "google";
        
        /**
         * Bing Search API密钥（微软官方API）
         * 官网：https://www.microsoft.com/en-us/bing/apis/bing-web-search-api
         */
        private String bingApiKey = "";
        
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
        
        public String getTavilyApiKey() {
            return tavilyApiKey;
        }
        
        public void setTavilyApiKey(String tavilyApiKey) {
            this.tavilyApiKey = tavilyApiKey;
        }
        
        public String getSerpApiKey() {
            return serpApiKey;
        }
        
        public void setSerpApiKey(String serpApiKey) {
            this.serpApiKey = serpApiKey;
        }
        
        public String getSerpEngine() {
            return serpEngine;
        }
        
        public void setSerpEngine(String serpEngine) {
            this.serpEngine = serpEngine;
        }
        
        public String getBingApiKey() {
            return bingApiKey;
        }
        
        public void setBingApiKey(String bingApiKey) {
            this.bingApiKey = bingApiKey;
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

