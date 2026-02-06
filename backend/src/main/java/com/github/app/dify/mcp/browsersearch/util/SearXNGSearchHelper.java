package com.github.app.dify.mcp.browsersearch.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.mcp.browsersearch.BrowserSearchConfig;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService.SearchResult;
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
 * SearX-NG搜索工具类
 * 提取SearX-NG搜索逻辑，避免循环依赖
 */
@Component
public class SearXNGSearchHelper {

    private static final Logger logger = LoggerFactory.getLogger(SearXNGSearchHelper.class);

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile WebClient webClient;

    /**
     * 使用SearX-NG搜索
     */
    public List<SearchResult> search(String query, int maxResults) {
        try {
            String baseUrl = browserSearchConfig.getSearxngBaseUrl();
            int timeout = browserSearchConfig.getTimeout();

            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String searchUrl = baseUrl + "/search?q=" + encodedQuery + "&format=json";

            String enginesToUse = browserSearchConfig.getDefaultEngines();
            if (enginesToUse != null && !enginesToUse.trim().isEmpty()) {
                String encodedEngines = java.net.URLEncoder.encode(enginesToUse.trim(), "UTF-8");
                searchUrl += "&engines=" + encodedEngines;
            }

            logger.info("========== SearX-NG搜索请求详情 ==========");
            logger.info("原始查询: {}", query);
            logger.info("URL编码后的查询: {}", encodedQuery);
            logger.info("使用的搜索引擎: {}", enginesToUse != null && !enginesToUse.trim().isEmpty() ? enginesToUse : "默认（SearX-NG配置）");
            logger.info("完整搜索URL: {}", searchUrl);
            logger.info("==========================================");

            String jsonResponse = null;
            try {
                logger.debug("准备发送SearX-NG搜索请求");

                jsonResponse = getWebClient()
                        .get()
                        .uri(searchUrl)
                        .exchangeToMono(response -> {
                            logger.info("SearX-NG响应状态: {}, 响应头: {}",
                                    response.statusCode(),
                                    response.headers().asHttpHeaders().entrySet().stream()
                                            .limit(10)
                                            .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                                            .collect(java.util.stream.Collectors.joining("; ")));

                            if (response.statusCode().is2xxSuccessful()) {
                                return response.bodyToMono(String.class);
                            } else {
                                return response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> {
                                            logger.error("SearX-NG返回错误状态: {}, 响应体: {}",
                                                    response.statusCode(),
                                                    body.length() > 500 ? body.substring(0, 500) : body);
                                            if (response.statusCode().value() == 403) {
                                                logger.error("SearX-NG返回403 Forbidden，可能原因：");
                                                logger.error("1) SearX-NG配置了访问限制（检查settings.yml中的allowed_ips或blocked_ips）");
                                                logger.error("2) 需要认证（检查是否需要API密钥）");
                                                logger.error("3) IP被限制或需要特定的User-Agent");
                                                logger.error("4) SearX-NG可能配置了CORS限制");
                                            }
                                            return Mono.just("");
                                        });
                            }
                        })
                        .timeout(Duration.ofSeconds(timeout))
                        .onErrorResume(Throwable.class, e -> {
                            Throwable cause = e;
                            while (cause.getCause() != null && cause != cause.getCause()) {
                                cause = cause.getCause();
                            }

                            if (cause instanceof java.util.concurrent.TimeoutException) {
                                logger.warn("SearX-NG搜索超时（{}秒） - 查询: {}", timeout, query);
                            } else {
                                logger.warn("SearX-NG搜索发生错误 - 查询: {}, 错误类型: {}",
                                        query, cause.getClass().getSimpleName(), cause);
                            }
                            return Mono.empty();
                        })
                        .defaultIfEmpty("")
                        .block();
            } catch (Exception e) {
                logger.error("SearX-NG搜索请求异常 - 查询: {}", query, e);
                return new ArrayList<>();
            }

            if (jsonResponse == null || jsonResponse.isEmpty()) {
                logger.warn("SearX-NG搜索返回空响应 - 查询: {}", query);
                return new ArrayList<>();
            }

            logger.info("SearX-NG搜索返回响应长度: {} - 查询: {}", jsonResponse.length(), query);

            String trimmedResponse = jsonResponse.trim();
            boolean isHtmlError = trimmedResponse.startsWith("<!") ||
                                 trimmedResponse.startsWith("<html") ||
                                 (trimmedResponse.startsWith("<") &&
                                  (trimmedResponse.toLowerCase().contains("<title>") ||
                                   trimmedResponse.toLowerCase().contains("<!doctype")));

            boolean isErrorPage = isHtmlError &&
                                 (trimmedResponse.toLowerCase().contains("forbidden") ||
                                  trimmedResponse.toLowerCase().contains("403") ||
                                  trimmedResponse.toLowerCase().contains("404") ||
                                  trimmedResponse.toLowerCase().contains("500"));

            if (isErrorPage) {
                logger.error("SearX-NG返回HTML错误页面而不是JSON - 查询: {}, 响应预览: {}",
                        query, trimmedResponse.length() > 500 ? trimmedResponse.substring(0, 500) : trimmedResponse);
                logger.error("可能的原因：1) SearX-NG服务配置了访问限制 2) 需要认证 3) URL路径不正确");
                return new ArrayList<>();
            }

            if (!trimmedResponse.startsWith("{") && !trimmedResponse.startsWith("[")) {
                logger.warn("SearX-NG返回的响应不是JSON格式 - 查询: {}, 响应开头: {}",
                        query, trimmedResponse.length() > 100 ? trimmedResponse.substring(0, 100) : trimmedResponse);
                return new ArrayList<>();
            }

            logger.info("========== SearX-NG JSON响应详情 ==========");
            logger.info("JSON响应长度: {} 字符", jsonResponse.length());
            String jsonPreview = jsonResponse.length() > 2000 ? jsonResponse.substring(0, 2000) + "..." : jsonResponse;
            logger.info("JSON响应预览（前2000字符）: {}", jsonPreview);

            try {
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                if (rootNode.has("query")) {
                    String returnedQuery = rootNode.get("query").asText();
                    logger.info("SearX-NG返回的查询字段: {}", returnedQuery);
                    String decodedReturnedQuery = java.net.URLDecoder.decode(returnedQuery, "UTF-8");
                    if (!decodedReturnedQuery.equals(query)) {
                        logger.warn("SearX-NG返回的查询与发送的查询不一致 - 发送: {}, 返回(解码后): {}", query, decodedReturnedQuery);
                    }
                }
                if (rootNode.has("number_of_results")) {
                    logger.info("SearX-NG返回的结果总数: {}", rootNode.get("number_of_results").asInt());
                }
            } catch (Exception e) {
                logger.debug("无法解析JSON响应以获取查询信息", e);
            }
            logger.info("==========================================");

            List<SearchResult> results = parseSearXNGResults(jsonResponse, maxResults, query);

            logger.info("SearX-NG搜索解析结果数量: {} - 查询: {}", results.size(), query);
            if (results.isEmpty()) {
                logger.warn("SearX-NG搜索未找到任何结果 - 查询: {}, JSON响应长度: {}", query, jsonResponse.length());
            }
            return results;

        } catch (Exception e) {
            logger.error("SearX-NG搜索失败 - 查询: {}", query, e);
            return new ArrayList<>();
        }
    }

    private List<SearchResult> parseSearXNGResults(String jsonResponse, int maxResults, String query) {
        List<SearchResult> results = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode resultsNode = rootNode.get("results");

            if (resultsNode == null) {
                logger.warn("SearX-NG返回的JSON中未找到results字段");
                if (rootNode.has("answers")) {
                    resultsNode = rootNode.get("answers");
                    logger.debug("找到answers字段，尝试使用");
                } else if (rootNode.isArray()) {
                    resultsNode = rootNode;
                    logger.debug("根节点是数组，直接使用");
                } else {
                    logger.warn("无法找到results数组，JSON结构: {}", rootNode.toPrettyString().substring(0, Math.min(500, rootNode.toPrettyString().length())));
                    return results;
                }
            }

            if (!resultsNode.isArray()) {
                logger.warn("SearX-NG返回的results不是数组类型，类型: {}", resultsNode.getNodeType());
                return results;
            }

            logger.info("SearX-NG results数组大小: {}", resultsNode.size());

            if (logger.isDebugEnabled()) {
                logger.debug("SearX-NG原始JSON结果（前3个）:");
                int debugCount = 0;
                for (JsonNode resultNode : resultsNode) {
                    if (debugCount >= 3) break;
                    logger.debug("结果 {} JSON: {}", debugCount + 1, resultNode.toPrettyString());
                    debugCount++;
                }
            }

            int count = 0;
            int skipped = 0;
            for (JsonNode resultNode : resultsNode) {
                if (count >= maxResults) {
                    break;
                }

                try {
                    String title = "";
                    String url = "";
                    String content = "";

                    if (resultNode.has("title")) {
                        title = resultNode.get("title").asText();
                    } else if (resultNode.has("name")) {
                        title = resultNode.get("name").asText();
                    }

                    if (resultNode.has("url")) {
                        url = resultNode.get("url").asText();
                    } else if (resultNode.has("link")) {
                        url = resultNode.get("link").asText();
                    } else if (resultNode.has("href")) {
                        url = resultNode.get("href").asText();
                    }

                    if (resultNode.has("content")) {
                        content = resultNode.get("content").asText();
                    } else if (resultNode.has("snippet")) {
                        content = resultNode.get("snippet").asText();
                    } else if (resultNode.has("description")) {
                        content = resultNode.get("description").asText();
                    } else if (resultNode.has("text")) {
                        content = resultNode.get("text").asText();
                    }

                    if (url != null && !url.isEmpty() &&
                        !url.contains("javascript:") &&
                        !url.startsWith("#") &&
                        title != null && !title.isEmpty() &&
                        title.length() >= 3) {

                        if (!isResultRelevant(title, url, content, query)) {
                            skipped++;
                            logger.info("跳过不相关结果 - 标题: '{}', URL: '{}', 查询: '{}'", title, url, query);
                            continue;
                        }

                        SearchResult result = new SearchResult();
                        result.setTitle(title.trim());
                        result.setUrl(url.trim());
                        result.setSnippet(content != null ? content.trim() : "");
                        results.add(result);
                        count++;

                        logger.debug("解析到搜索结果 {}: {} - {}", count, title, url);
                    } else {
                        skipped++;
                        logger.debug("跳过无效结果 - title: '{}', url: '{}'", title, url);
                    }
                } catch (Exception e) {
                    skipped++;
                    logger.warn("解析单个搜索结果失败，跳过该项", e);
                    continue;
                }
            }

            logger.info("SearX-NG解析完成 - 找到 {} 个有效结果，跳过 {} 个无效结果", results.size(), skipped);

        } catch (Exception e) {
            logger.error("解析SearX-NG搜索结果失败", e);
            String jsonPreview = jsonResponse.length() > 1000 ? jsonResponse.substring(0, 1000) : jsonResponse;
            logger.warn("JSON响应预览（解析失败）: {}", jsonPreview);
        }

        return results;
    }

    private boolean isResultRelevant(String title, String url, String content, String query) {
        if (title == null || url == null || query == null) {
            return true;
        }

        String lowerTitle = title.toLowerCase();
        String lowerUrl = url.toLowerCase();
        String lowerContent = content != null ? content.toLowerCase() : "";
        String lowerQuery = query.toLowerCase();

        String[] stopWords = {"的", "了", "在", "是", "有", "和", "与", "或", "但", "就", "都", "也", "还", "什么", "怎么", "如何",
                             "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "what", "how"};

        java.util.List<String> keywords = new java.util.ArrayList<>();

        java.util.regex.Pattern chinesePattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
        java.util.regex.Matcher chineseMatcher = chinesePattern.matcher(lowerQuery);
        while (chineseMatcher.find()) {
            String chineseWord = chineseMatcher.group();
            if (chineseWord.length() >= 2 && !java.util.Arrays.asList(stopWords).contains(chineseWord)) {
                keywords.add(chineseWord);
            }
        }

        String[] queryWords = lowerQuery.split("\\s+");
        for (String word : queryWords) {
            word = word.trim().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
            if (word.length() > 1 && !java.util.Arrays.asList(stopWords).contains(word) && !keywords.contains(word)) {
                keywords.add(word);
            }
        }

        if (keywords.size() < 1) {
            return true;
        }

        int matchCount = 0;
        int importantMatchCount = 0;

        for (String keyword : keywords) {
            if (keyword.length() >= 2) {
                boolean matched = lowerTitle.contains(keyword) || lowerUrl.contains(keyword) || lowerContent.contains(keyword);
                if (matched) {
                    matchCount++;
                    if (keyword.length() >= 3 || keyword.matches("[\\u4e00-\\u9fa5]+")) {
                        importantMatchCount++;
                    }
                }
            }
        }

        double relevanceRatio = keywords.size() > 0 ? (double) matchCount / keywords.size() : 0.0;
        boolean isRelevant;

        if (keywords.size() <= 2) {
            isRelevant = matchCount == keywords.size();
        } else {
            isRelevant = relevanceRatio >= 0.5 || (importantMatchCount >= 2 && relevanceRatio >= 0.4);
        }

        if (!isRelevant) {
            logger.info("结果不相关 - 标题: '{}', 匹配关键词数: {}/{}, 重要关键词匹配: {}, 相关性: {:.2f}, 查询: '{}'",
                    title, matchCount, keywords.size(), importantMatchCount, relevanceRatio, query);
        }

        return isRelevant;
    }

    private WebClient getWebClient() {
        if (webClient == null) {
            synchronized (this) {
                if (webClient == null) {
                    String baseUrl = browserSearchConfig.getSearxngBaseUrl();
                    webClient = WebClient.builder()
                            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/html, application/xhtml+xml, */*")
                            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8")
                            .defaultHeader(HttpHeaders.REFERER, baseUrl + "/")
                            .defaultHeader("X-Requested-With", "XMLHttpRequest")
                            .build();
                    logger.info("SearXNGSearchHelper WebClient已创建 - SearX-NG地址: {}", baseUrl);
                }
            }
        }
        return webClient;
    }
}
