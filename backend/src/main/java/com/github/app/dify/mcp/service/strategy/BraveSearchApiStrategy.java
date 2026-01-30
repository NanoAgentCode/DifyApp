package com.github.app.dify.mcp.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.app.dify.mcp.config.McpConfig;
import com.github.app.dify.mcp.service.McpBrowserSearchService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Brave Search API 策略实现
 * 使用 Brave 官方 Web Search API
 * 官网：https://brave.com/search/api
 * 文档：https://api.search.brave.com/res/v1/web/search
 */
@Component
public class BraveSearchApiStrategy extends BaseSearchApiStrategy {

    private static final String BRAVE_WEB_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search";

    @Autowired
    private McpConfig mcpConfig;

    @Override
    public String getType() {
        return "brave";
    }

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public boolean isAvailable() {
        String apiKey = mcpConfig.getBrowserSearch().getBraveApiKey();
        return isApiKeyValid(apiKey);
    }

    @Override
    protected List<SearchResult> performSearch(String query, int maxResults) throws Exception {
        String apiKey = mcpConfig.getBrowserSearch().getBraveApiKey();
        int timeout = mcpConfig.getBrowserSearch().getTimeout();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        int count = Math.min(Math.max(maxResults, 1), 20);
        String apiUrl = String.format("%s?q=%s&count=%d", BRAVE_WEB_SEARCH_URL, encodedQuery, count);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Subscription-Token", apiKey);

        String jsonResponse = executeGetRequest(apiUrl, headers, timeout);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return new ArrayList<>();
        }

        return parseBraveResults(jsonResponse, maxResults);
    }

    private List<SearchResult> parseBraveResults(String jsonResponse, int maxResults) {
        List<SearchResult> results = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode webNode = rootNode.get("web");
            if (webNode == null) {
                return results;
            }
            JsonNode resultsNode = webNode.get("results");
            if (resultsNode == null || !resultsNode.isArray()) {
                return results;
            }

            int count = 0;
            for (JsonNode resultNode : resultsNode) {
                if (count >= maxResults) {
                    break;
                }
                String title = resultNode.has("title") ? resultNode.get("title").asText() : "";
                String url = resultNode.has("url") ? resultNode.get("url").asText() : "";
                String description = resultNode.has("description") ? resultNode.get("description").asText() : "";

                if (!url.isEmpty() && !title.isEmpty()) {
                    SearchResult result = new SearchResult();
                    result.setTitle(title);
                    result.setUrl(url);
                    result.setSnippet(description);
                    results.add(result);
                    count++;
                }
            }

            logger.info("Brave Search 解析到 {} 个结果", results.size());

        } catch (Exception e) {
            logger.error("解析 Brave Search 结果失败", e);
        }

        return results;
    }

    @Override
    public String getApiName() {
        return "Brave Search API";
    }
}
