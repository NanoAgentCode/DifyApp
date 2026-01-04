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
 * SerpAPI搜索策略实现
 * SerpAPI提供Google、Bing、Yahoo等搜索引擎的API访问
 * 官网：https://serpapi.com
 * 优势：支持多个搜索引擎、稳定可靠、高质量结果
 */
@Component
public class SerpApiSearchStrategy implements SearchApiStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(SerpApiSearchStrategy.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile WebClient webClient;
    
    @Override
    public String getType() {
        return "serpapi";
    }
    
    @Override
    public int getPriority() {
        return 2;
    }
    
    @Override
    public boolean isAvailable() {
        String apiKey = mcpConfig.getBrowserSearch().getSerpApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            logger.warn("SerpAPI未配置，跳过搜索");
            return new ArrayList<>();
        }
        
        try {
            String apiKey = mcpConfig.getBrowserSearch().getSerpApiKey();
            String engine = mcpConfig.getBrowserSearch().getSerpEngine() != null 
                ? mcpConfig.getBrowserSearch().getSerpEngine() : "google";
            
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl = String.format(
                "https://serpapi.com/search.json?engine=%s&q=%s&api_key=%s&num=%d",
                engine, encodedQuery, apiKey, maxResults
            );
            
            logger.info("使用SerpAPI搜索 - 引擎: {}, 查询: {}, 最大结果数: {}", engine, query, maxResults);
            
            String jsonResponse = getWebClient()
                .get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    logger.warn("SerpAPI请求失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
            
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return new ArrayList<>();
            }
            
            return parseSerpApiResults(jsonResponse, maxResults);
            
        } catch (Exception e) {
            logger.error("SerpAPI搜索失败 - 查询: {}", query, e);
            return new ArrayList<>();
        }
    }
    
    private List<SearchResult> parseSerpApiResults(String jsonResponse, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // SerpAPI返回格式：{"organic_results": [...]}
            JsonNode organicResults = rootNode.get("organic_results");
            if (organicResults != null && organicResults.isArray()) {
                int count = 0;
                for (JsonNode resultNode : organicResults) {
                    if (count >= maxResults) break;
                    
                    String title = resultNode.has("title") ? resultNode.get("title").asText() : "";
                    String url = resultNode.has("link") ? resultNode.get("link").asText() : "";
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
            
            logger.info("SerpAPI解析到 {} 个结果", results.size());
            
        } catch (Exception e) {
            logger.error("解析SerpAPI结果失败", e);
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
        return "SerpAPI (Google/Bing)";
    }
}

