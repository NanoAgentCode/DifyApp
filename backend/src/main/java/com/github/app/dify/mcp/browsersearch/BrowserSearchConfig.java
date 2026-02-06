package com.github.app.dify.mcp.browsersearch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 浏览器搜索服务配置
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.browser-search")
public class BrowserSearchConfig {

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
     * Brave Search API密钥（Brave 官方网页搜索 API）
     * 官网：https://brave.com/search/api
     */
    private String braveApiKey = "";

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

    public String getBraveApiKey() {
        return braveApiKey;
    }

    public void setBraveApiKey(String braveApiKey) {
        this.braveApiKey = braveApiKey;
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
