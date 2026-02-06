package com.github.app.dify.mcp.browsersearch.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.app.dify.mcp.browsersearch.BrowserSearchConfig;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bing搜索API策略实现
 * 使用微软Bing Search API v7
 * 官网：https://www.microsoft.com/en-us/bing/apis/bing-web-search-api
 */
@Component
public class BingSearchApiStrategy extends BaseSearchApiStrategy {

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

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
        String apiKey = browserSearchConfig.getBingApiKey();
        return isApiKeyValid(apiKey);
    }

    @Override
    protected List<SearchResult> performSearch(String query, int maxResults) throws Exception {
        String apiKey = browserSearchConfig.getBingApiKey();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String apiUrl = String.format(
            "https://api.bing.microsoft.com/v7.0/search?q=%s&count=%d",
            encodedQuery, maxResults
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("Ocp-Apim-Subscription-Key", apiKey);

        String jsonResponse = executeGetRequest(apiUrl, headers, 10);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return new ArrayList<>();
        }

        return parseBingResults(jsonResponse, maxResults);
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

    @Override
    public String getApiName() {
        return "Bing Search API";
    }
}
