package com.github.app.dify.mcp.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.mcp.config.McpConfig;
import com.github.app.dify.mcp.service.McpBrowserSearchService.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Bing搜索API策略实现
 * 使用微软Bing Search API v7
 * 官网：https://www.microsoft.com/en-us/bing/apis/bing-web-search-api
 * 优势：官方API、稳定可靠、高质量结果
 */
@Component
public class BingSearchApiStrategy implements SearchApiStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(BingSearchApiStrategy.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile WebClient webClient;
    
    @Override
    public String getType() {
        return "bing";
    }
    
    @Override
    public int getPriority() {
        return 3;
    }
    
    @Override
    public boolean isAvailable() {
        String apiKey = mcpConfig.getBrowserSearch().getBingApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            logger.warn("Bing API未配置，跳过搜索");
            return new ArrayList<>();
        }
        
        try {
            String apiKey = mcpConfig.getBrowserSearch().getBingApiKey();
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl = String.format(
                "https://api.bing.microsoft.com/v7.0/search?q=%s&count=%d",
                encodedQuery, maxResults
            );
            
            logger.info("使用Bing API搜索 - 查询: {}, 最大结果数: {}", query, maxResults);
            
            String jsonResponse = getWebClient()
                .get()
                .uri(apiUrl)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    logger.warn("Bing API请求失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
            
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return new ArrayList<>();
            }
            
            return parseBingResults(jsonResponse, maxResults);
            
        } catch (Exception e) {
            logger.error("Bing搜索失败 - 查询: {}", query, e);
            return new ArrayList<>();
        }
    }
    
    private List<SearchResult> parseBingResults(String jsonResponse, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Bing返回格式：{"webPages": {"value": [...]}}
            JsonNode webPages = rootNode.get("webPages");
            if (webPages != null) {
                JsonNode value = webPages.get("value");
                if (value != null && value.isArray()) {
                    int count = 0;
                    for (JsonNode resultNode : value) {
                        if (count >= maxResults) break;
                        
                        String title = resultNode.has("name") ? resultNode.get("name").asText() : "";
                        String url = resultNode.has("url") ? resultNode.get("url").asText() : "";
                        String snippet = resultNode.has("snippet") ? resultNode.get("snippet").asText() : "";
                        
                        if (!url.isEmpty() && !title.isEmpty()) {
                            SearchResult result = new SearchResult();
                            result.setTitle(title);
                            result.setUrl(url);
                            result.setSnippet(snippet);
                            results.add(result);
                            count++;
                        }
                    }
                }
            }
            
            logger.info("Bing解析到 {} 个结果", results.size());
            
        } catch (Exception e) {
            logger.error("解析Bing结果失败", e);
        }
        
        return results;
    }
    
    private WebClient getWebClient() {
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
    
    @Override
    public String getApiName() {
        return "Bing Search API";
    }
}

