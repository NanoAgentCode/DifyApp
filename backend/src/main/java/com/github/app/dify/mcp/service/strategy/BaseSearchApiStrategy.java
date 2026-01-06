package com.github.app.dify.mcp.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.mcp.service.McpBrowserSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for SearchApiStrategy implementations
 * Provides common functionality for API-based search strategies
 */
public abstract class BaseSearchApiStrategy implements SearchApiStrategy {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected volatile WebClient webClient;
    
    /**
     * Get WebClient with lazy initialization and double-checked locking
     */
    protected WebClient getWebClient() {
        if (webClient == null) {
            synchronized (this) {
                if (webClient == null) {
                    webClient = WebClient.builder()
                        .defaultHeader(HttpHeaders.USER_AGENT, "DifyApp/1.0")
                        .build();
                }
            }
        }
        return webClient;
    }
    
    /**
     * Execute HTTP GET request with error handling
     * 
     * @param url API URL
     * @param headers additional headers to include
     * @param timeoutSeconds timeout in seconds
     * @return response body as String, or empty String if error occurs
     */
    protected String executeGetRequest(String url, java.util.Map<String, String> headers, int timeoutSeconds) {
        try {
            WebClient.RequestHeadersSpec<?> request = getWebClient()
                .get()
                .uri(url);
            
            // Add custom headers
            if (headers != null) {
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    request = request.header(entry.getKey(), entry.getValue());
                }
            }
            
            return request
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(e -> {
                    logger.warn("API请求失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
                
        } catch (Exception e) {
            logger.error("执行GET请求失败 - URL: {}", url, e);
            return "";
        }
    }
    
    /**
     * Execute HTTP POST request with error handling
     * 
     * @param url API URL
     * @param headers additional headers to include
     * @param body request body
     * @param timeoutSeconds timeout in seconds
     * @return response body as String, or empty String if error occurs
     */
    protected String executePostRequest(String url, java.util.Map<String, String> headers, String body, int timeoutSeconds) {
        try {
            WebClient.RequestHeadersSpec<?> request = getWebClient()
                .post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(body);
            
            // Add custom headers
            if (headers != null) {
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    request = request.header(entry.getKey(), entry.getValue());
                }
            }
            
            return request
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(e -> {
                    logger.warn("API请求失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
                
        } catch (Exception e) {
            logger.error("执行POST请求失败 - URL: {}", url, e);
            return "";
        }
    }
    
    /**
     * Safe search execution with error handling
     * Template method that subclasses override
     */
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            logger.warn("{} API未配置，跳过搜索", getApiName());
            return new ArrayList<>();
        }
        
        try {
            logger.info("使用{} API搜索 - 查询: {}, 最大结果数: {}", getApiName(), query, maxResults);
            return performSearch(query, maxResults);
        } catch (Exception e) {
            logger.error("{}搜索失败 - 查询: {}", getApiName(), query, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Perform the actual search - subclasses must implement this
     * 
     * @param query search query
     * @param maxResults maximum number of results
     * @return list of search results
     */
    protected abstract List<SearchResult> performSearch(String query, int maxResults) throws Exception;
    
    /**
     * Check if API key is configured and not empty
     * 
     * @param apiKey the API key to check
     * @return true if API key is valid, false otherwise
     */
    protected boolean isApiKeyValid(String apiKey) {
        return apiKey != null && !apiKey.trim().isEmpty();
    }
}
