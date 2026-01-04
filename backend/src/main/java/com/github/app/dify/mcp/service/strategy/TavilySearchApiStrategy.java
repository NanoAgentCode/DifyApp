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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tavily搜索API策略实现
 * Tavily是专为AI应用设计的搜索API，返回结构化、高质量的结果
 * 官网：https://tavily.com
 * 优势：专为LLM优化、结构化结果、高质量内容、快速响应
 */
@Component
public class TavilySearchApiStrategy implements SearchApiStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(TavilySearchApiStrategy.class);
    
    @Autowired
    private McpConfig mcpConfig;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile WebClient webClient;
    
    @Override
    public String getType() {
        return "tavily";
    }
    
    @Override
    public int getPriority() {
        return 1; // 最高优先级
    }
    
    @Override
    public boolean isAvailable() {
        String apiKey = mcpConfig.getBrowserSearch().getTavilyApiKey();
        return apiKey != null && !apiKey.trim().isEmpty();
    }
    
    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (!isAvailable()) {
            logger.warn("Tavily API未配置，跳过搜索");
            return new ArrayList<>();
        }
        
        try {
            String apiKey = mcpConfig.getBrowserSearch().getTavilyApiKey();
            String apiUrl = "https://api.tavily.com/search";
            
            // 构建请求体（使用ObjectMapper确保JSON格式正确）
            java.util.Map<String, Object> requestMap = new java.util.HashMap<>();
            requestMap.put("api_key", apiKey);
            requestMap.put("query", query);
            requestMap.put("max_results", maxResults);
            requestMap.put("include_answer", true);
            requestMap.put("include_raw_content", false);
            
            String requestBody = objectMapper.writeValueAsString(requestMap);
            
            logger.info("使用Tavily API搜索 - 查询: {}, 最大结果数: {}", query, maxResults);
            
            String jsonResponse = getWebClient()
                .post()
                .uri(apiUrl)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    logger.warn("Tavily API请求失败: {}", e.getMessage());
                    return Mono.empty();
                })
                .block();
            
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return new ArrayList<>();
            }
            
            return parseTavilyResults(jsonResponse, maxResults);
            
        } catch (Exception e) {
            logger.error("Tavily搜索失败 - 查询: {}", query, e);
            return new ArrayList<>();
        }
    }
    
    private List<SearchResult> parseTavilyResults(String jsonResponse, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tavily返回格式：{"results": [...], "answer": "..."}
            JsonNode resultsNode = rootNode.get("results");
            if (resultsNode != null && resultsNode.isArray()) {
                int count = 0;
                for (JsonNode resultNode : resultsNode) {
                    if (count >= maxResults) break;
                    
                    String title = resultNode.has("title") ? resultNode.get("title").asText() : "";
                    String url = resultNode.has("url") ? resultNode.get("url").asText() : "";
                    String content = resultNode.has("content") ? resultNode.get("content").asText() : "";
                    
                    if (!url.isEmpty() && !title.isEmpty()) {
                        SearchResult result = new SearchResult();
                        result.setTitle(title);
                        result.setUrl(url);
                        result.setSnippet(content);
                        results.add(result);
                        count++;
                    }
                }
            }
            
            logger.info("Tavily解析到 {} 个结果", results.size());
            
        } catch (Exception e) {
            logger.error("解析Tavily结果失败", e);
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
        return "Tavily (AI优化搜索)";
    }
}

