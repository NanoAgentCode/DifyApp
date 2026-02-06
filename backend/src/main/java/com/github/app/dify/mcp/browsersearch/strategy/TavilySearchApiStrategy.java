package com.github.app.dify.mcp.browsersearch.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.app.dify.mcp.browsersearch.BrowserSearchConfig;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tavily搜索API策略实现
 * Tavily是专为AI应用设计的搜索API，返回结构化、高质量的结果
 * 官网：https://tavily.com
 */
@Component
public class TavilySearchApiStrategy extends BaseSearchApiStrategy {

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

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
        String apiKey = browserSearchConfig.getTavilyApiKey();
        return isApiKeyValid(apiKey);
    }

    @Override
    protected List<SearchResult> performSearch(String query, int maxResults) throws Exception {
        String apiKey = browserSearchConfig.getTavilyApiKey();
        String apiUrl = "https://api.tavily.com/search";

        // 构建请求体
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("api_key", apiKey);
        requestMap.put("query", query);
        requestMap.put("max_results", maxResults);
        requestMap.put("include_answer", true);
        requestMap.put("include_raw_content", false);

        String requestBody = objectMapper.writeValueAsString(requestMap);
        String jsonResponse = executePostRequest(apiUrl, null, requestBody, 10);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return new ArrayList<>();
        }

        return parseTavilyResults(jsonResponse, maxResults);
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

    @Override
    public String getApiName() {
        return "Tavily (AI优化搜索)";
    }
}
