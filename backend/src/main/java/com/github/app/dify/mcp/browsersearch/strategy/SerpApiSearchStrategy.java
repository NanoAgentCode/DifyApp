package com.github.app.dify.mcp.browsersearch.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.app.dify.mcp.browsersearch.BrowserSearchConfig;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SerpAPI搜索策略实现
 * SerpAPI提供Google、Bing、Yahoo等搜索引擎的API访问
 * 官网：https://serpapi.com
 */
@Component
public class SerpApiSearchStrategy extends BaseSearchApiStrategy {

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

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
        String apiKey = browserSearchConfig.getSerpApiKey();
        return isApiKeyValid(apiKey);
    }

    @Override
    protected List<SearchResult> performSearch(String query, int maxResults) throws Exception {
        String apiKey = browserSearchConfig.getSerpApiKey();
        String engine = browserSearchConfig.getSerpEngine() != null
            ? browserSearchConfig.getSerpEngine() : "google";

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String apiUrl = String.format(
            "https://serpapi.com/search.json?engine=%s&q=%s&api_key=%s&num=%d",
            engine, encodedQuery, apiKey, maxResults
        );

        logger.info("使用SerpAPI搜索 - 引擎: {}, 查询: {}", engine, query);

        String jsonResponse = executeGetRequest(apiUrl, null, 15);

        if (jsonResponse == null || jsonResponse.isEmpty()) {
            return new ArrayList<>();
        }

        return parseSerpApiResults(jsonResponse, maxResults);
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

    @Override
    public String getApiName() {
        return "SerpAPI (Google/Bing)";
    }
}
