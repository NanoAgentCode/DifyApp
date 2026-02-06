package com.github.app.dify.mcp.browsersearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.mcp.browsersearch.strategy.SearchApiFactory;
import com.github.app.dify.mcp.browsersearch.util.SearXNGSearchHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
/**
 * MCP浏览器检索服务
 * 支持多种搜索API，自动选择最优方案并支持降级
 * 支持的API：Tavily（推荐）、Brave Search、SerpAPI、Bing、SearX-NG（降级）
 */
@Service
public class McpBrowserSearchService {

    private static final Logger logger = LoggerFactory.getLogger(McpBrowserSearchService.class);

    @Autowired
    private BrowserSearchConfig browserSearchConfig;

    @Autowired(required = false)
    private SearchApiFactory searchApiFactory;

    @Autowired(required = false)
    private SearXNGSearchHelper searXNGSearchHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // WebClient实例（复用，避免每次创建）
    // 使用volatile确保多线程环境下的可见性
    private volatile WebClient webClient;

    /**
     * 获取或创建WebClient实例
     * 使用双重检查锁定模式确保线程安全
     */
    private WebClient getWebClient() {
        // 第一次检查（无锁）
        WebClient result = webClient;
        if (result == null) {
            // 同步块，确保只有一个线程能创建实例
            synchronized (this) {
                // 第二次检查（有锁），防止其他线程已经创建了实例
                result = webClient;
                if (result == null) {
                    String baseUrl = browserSearchConfig.getSearxngBaseUrl();
                    result = WebClient.builder()
                            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/html, application/xhtml+xml, */*")
                            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8")
                            .defaultHeader(HttpHeaders.REFERER, baseUrl + "/")
                            .defaultHeader("X-Requested-With", "XMLHttpRequest")
                            .build();
                    webClient = result; // 写入volatile变量，确保对其他线程可见
                    logger.info("McpBrowserSearchService WebClient已创建 - SearX-NG地址: {}", baseUrl);
                }
            }
        }
        return result;
    }

    /**
     * 执行浏览器检索
     * 优先使用商业API（Tavily、SerpAPI、Bing），如果都不可用则降级到SearX-NG
     * @param query 搜索查询
     * @param maxResults 最大结果数量（如果<=0，使用默认值）
     * @return 检索结果列表
     */
    public List<SearchResult> search(String query, int maxResults) {
        if (query == null || query.trim().isEmpty()) {
            logger.warn("搜索查询为空，返回空结果");
            return new ArrayList<>();
        }

        // 使用默认值如果未指定
        if (maxResults <= 0) {
            maxResults = browserSearchConfig.getDefaultMaxResults();
        }

        try {
            logger.info("开始浏览器检索 - 查询: {}, 最大结果数: {}", query, maxResults);

            // 优化搜索查询，添加时间限制以确保获取最新信息
            String optimizedQuery = browserSearchConfig.isEnableQueryOptimization()
                    ? optimizeQueryForLatestInfo(query)
                    : query;
            if (!optimizedQuery.equals(query)) {
                logger.debug("优化后的搜索查询: {}", optimizedQuery);
            }

            // 优先使用新的多API工厂（支持自动降级）
            List<SearchResult> results = null;
            if (searchApiFactory != null) {
                try {
                    results = searchApiFactory.search(optimizedQuery, maxResults);
                    if (results != null && !results.isEmpty()) {
                        logger.info("使用多API工厂搜索成功，返回 {} 个结果", results.size());
                    }
                } catch (Exception e) {
                    logger.warn("多API工厂搜索失败，降级到SearX-NG: {}", e.getMessage());
                }
            }

            // 如果多API工厂不可用或返回空结果，降级到原有的SearX-NG实现
            if (results == null || results.isEmpty()) {
                logger.info("降级使用SearX-NG搜索");
                if (searXNGSearchHelper != null) {
                    try {
                        results = searXNGSearchHelper.search(optimizedQuery, maxResults);
                    } catch (Exception e) {
                        logger.warn("SearX-NG搜索失败", e);
                    }
                } else {
                    // 如果SearXNGSearchHelper也不可用，使用原有的searchWithSearXNG方法
                    results = searchWithSearXNG(optimizedQuery, maxResults);
                }
            }

            logger.info("浏览器检索完成 - 查询: {}, 结果数量: {}", query, results != null ? results.size() : 0);

            // 打印详细的搜索结果
            if (results != null && !results.isEmpty()) {
                logger.info("========== 浏览器搜索结果详情 ==========");
                logger.info("原始查询: {}", query);
                logger.info("优化查询: {}", optimizedQuery);
                logger.info("结果总数: {}", results.size());
                for (int i = 0; i < results.size(); i++) {
                    SearchResult result = results.get(i);
                    logger.info("--- 结果 {} ---", i + 1);
                    logger.info("标题: {}", result.getTitle());
                    logger.info("URL: {}", result.getUrl());
                    logger.info("摘要: {}", result.getSnippet() != null && !result.getSnippet().isEmpty()
                            ? result.getSnippet() : "(无摘要)");
                }
                logger.info("========================================");
            } else {
                logger.warn("浏览器检索未找到任何结果 - 查询: {}, 优化查询: {}", query, optimizedQuery);
            }

            return results != null ? results : new ArrayList<>();

        } catch (Exception e) {
            logger.error("浏览器检索失败 - 查询: {}", query, e);
            // 返回空结果，不抛出异常，避免影响主流程
            return new ArrayList<>();
        }
    }

    /**
     * 优化搜索查询，添加时间限制以确保获取最新信息（通用优化，适用于所有查询类型）
     */
    private String optimizeQueryForLatestInfo(String originalQuery) {
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            return originalQuery;
        }

        String lowerQuery = originalQuery.toLowerCase();
        String optimizedQuery = originalQuery;

        // 获取当前年份
        int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String currentYearStr = String.valueOf(currentYear);

        // 检查查询中是否已经包含年份信息
        boolean hasYear = originalQuery.matches(".*\\b(19|20)\\d{2}\\b.*");
        boolean hasLatestKeyword = lowerQuery.contains("最新") ||
                                   lowerQuery.contains("最近") ||
                                   lowerQuery.contains("近期") ||
                                   lowerQuery.contains("lately") ||
                                   lowerQuery.contains("recent") ||
                                   lowerQuery.contains("latest");

        // 如果查询中没有年份且没有"最新"等关键词，且查询看起来需要最新信息，添加时间限制
        if (!hasYear && !hasLatestKeyword) {
            // 判断是否为时效性查询（包含常见的时间敏感关键词）
            // 这是一个通用的判断，适用于新闻、事件、数据、状态等各种查询
            boolean isTimeSensitive = lowerQuery.contains("言论") ||
                                     lowerQuery.contains("发表") ||
                                     lowerQuery.contains("新闻") ||
                                     lowerQuery.contains("事件") ||
                                     lowerQuery.contains("消息") ||
                                     lowerQuery.contains("动态") ||
                                     lowerQuery.contains("情况") ||
                                     lowerQuery.contains("状态") ||
                                     lowerQuery.contains("火灾") ||
                                     lowerQuery.contains("事故") ||
                                     lowerQuery.contains("时间") ||
                                     lowerQuery.contains("日期") ||
                                     lowerQuery.contains("今天") ||
                                     lowerQuery.contains("现在") ||
                                     lowerQuery.contains("当前") ||
                                     lowerQuery.contains("statement") ||
                                     lowerQuery.contains("news") ||
                                     lowerQuery.contains("event") ||
                                     lowerQuery.contains("fire") ||
                                     lowerQuery.contains("accident") ||
                                     lowerQuery.contains("today") ||
                                     lowerQuery.contains("now") ||
                                     lowerQuery.contains("current");

            if (isTimeSensitive) {
                optimizedQuery = originalQuery + " " + currentYearStr + " 最新";
                logger.debug("时效性查询优化: {} -> {}", originalQuery, optimizedQuery);
            }
        }

        return optimizedQuery;
    }

    /**
     * 使用SearX-NG搜索
     * @param query 搜索查询
     * @param maxResults 最大结果数量
     */
    private List<SearchResult> searchWithSearXNG(String query, int maxResults) {
        try {
            String baseUrl = browserSearchConfig.getSearxngBaseUrl();
            int timeout = browserSearchConfig.getTimeout();

            // SearX-NG的搜索API端点
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String searchUrl = baseUrl + "/search?q=" + encodedQuery + "&format=json";

            // 使用配置的默认搜索引擎
            String enginesToUse = browserSearchConfig.getDefaultEngines();

            // 如果配置了默认搜索引擎，添加到URL参数中
            // 注意：某些SearX-NG配置可能不支持engines参数，如果不支持会自动忽略
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
                            // 记录响应状态和头信息用于调试
                            logger.info("SearX-NG响应状态: {}, 响应头: {}",
                                    response.statusCode(),
                                    response.headers().asHttpHeaders().entrySet().stream()
                                            .limit(10)  // 限制日志长度
                                            .map(e -> e.getKey() + "=" + String.join(",", e.getValue()))
                                            .collect(java.util.stream.Collectors.joining("; ")));

                            if (response.statusCode().is2xxSuccessful()) {
                                return response.bodyToMono(String.class);
                            } else {
                                // 对于非2xx响应，也读取响应体以便诊断
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
                                            return reactor.core.publisher.Mono.just("");
                                        });
                            }
                        })
                        .timeout(Duration.ofSeconds(timeout))
                        .onErrorResume(Throwable.class, e -> {
                            // 处理所有异常，包括超时异常
                            Throwable cause = e;
                            // 如果是包装异常，尝试获取根原因
                            while (cause.getCause() != null && cause != cause.getCause()) {
                                cause = cause.getCause();
                            }

                            if (cause instanceof java.util.concurrent.TimeoutException) {
                                logger.warn("SearX-NG搜索超时（10秒） - 查询: {}", query);
                            } else {
                                logger.warn("SearX-NG搜索发生错误 - 查询: {}, 错误类型: {}",
                                        query, cause.getClass().getSimpleName(), cause);
                            }
                            return reactor.core.publisher.Mono.empty();
                        })
                        .defaultIfEmpty("")  // 如果Mono为空，返回空字符串
                        .block();
            } catch (Exception e) {
                logger.error("SearX-NG搜索请求异常 - 查询: {}", query, e);
                // 捕获所有异常，返回空结果，避免影响主流程
                return new ArrayList<>();
            }

            if (jsonResponse == null || jsonResponse.isEmpty()) {
                logger.warn("SearX-NG搜索返回空响应 - 查询: {}", query);
                return new ArrayList<>();
            }

            logger.info("SearX-NG搜索返回响应长度: {} - 查询: {}", jsonResponse.length(), query);

            // 检查响应是否是HTML错误页面（如403 Forbidden）
            // 注意：只检查响应开头，避免误判JSON中包含"html"字符串的情况
            String trimmedResponse = jsonResponse.trim();
            boolean isHtmlError = trimmedResponse.startsWith("<!") ||
                                 trimmedResponse.startsWith("<html") ||
                                 (trimmedResponse.startsWith("<") &&
                                  (trimmedResponse.toLowerCase().contains("<title>") ||
                                   trimmedResponse.toLowerCase().contains("<!doctype")));

            // 检查是否是明显的错误页面（包含错误状态码和错误信息）
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

            // 如果响应以{或[开头，尝试解析为JSON（即使包含"html"字符串也可能是有效的JSON）
            if (!trimmedResponse.startsWith("{") && !trimmedResponse.startsWith("[")) {
                logger.warn("SearX-NG返回的响应不是JSON格式 - 查询: {}, 响应开头: {}",
                        query, trimmedResponse.length() > 100 ? trimmedResponse.substring(0, 100) : trimmedResponse);
                return new ArrayList<>();
            }

            // 记录JSON响应的详细信息用于调试
            logger.info("========== SearX-NG JSON响应详情 ==========");
            logger.info("JSON响应长度: {} 字符", jsonResponse.length());
            String jsonPreview = jsonResponse.length() > 2000 ? jsonResponse.substring(0, 2000) + "..." : jsonResponse;
            logger.info("JSON响应预览（前2000字符）: {}", jsonPreview);

            // 尝试解析并显示查询字段（SearX-NG返回的JSON中通常包含query字段）
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

            // 解析JSON响应
            List<SearchResult> results = parseSearXNGResults(jsonResponse, maxResults, query);

            logger.info("SearX-NG搜索解析结果数量: {} - 查询: {}", results.size(), query);
            if (results.isEmpty()) {
                logger.warn("SearX-NG搜索未找到任何结果 - 查询: {}, JSON响应长度: {}", query, jsonResponse.length());
            }
            return results;

        } catch (Exception e) {
            logger.error("SearX-NG搜索失败 - 查询: {}", query, e);
            // 如果SearX-NG搜索失败，返回空结果
            return new ArrayList<>();
        }
    }

    /**
     * 解析SearX-NG的JSON搜索结果
     */
    private List<SearchResult> parseSearXNGResults(String jsonResponse, int maxResults, String query) {
        List<SearchResult> results = new ArrayList<>();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            // 记录JSON结构用于调试
            java.util.Iterator<String> fieldNames = rootNode.fieldNames();
            java.util.List<String> fields = new java.util.ArrayList<>();
            while (fieldNames.hasNext()) {
                fields.add(fieldNames.next());
            }
            logger.debug("SearX-NG JSON根节点字段: {}", fields.isEmpty() ? "无字段" : String.join(", ", fields));

            // SearX-NG的JSON格式：{"results": [{"title": "...", "url": "...", "content": "...", ...}, ...]}
            JsonNode resultsNode = rootNode.get("results");

            if (resultsNode == null) {
                logger.warn("SearX-NG返回的JSON中未找到results字段");
                // 尝试其他可能的字段名
                if (rootNode.has("answers")) {
                    resultsNode = rootNode.get("answers");
                    logger.debug("找到answers字段，尝试使用");
                } else if (rootNode.isArray()) {
                    // 如果根节点本身就是数组
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

            // 打印原始JSON结果用于调试
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
                    // 尝试多种可能的字段名
                    String title = "";
                    String url = "";
                    String content = "";

                    // 标题字段
                    if (resultNode.has("title")) {
                        title = resultNode.get("title").asText();
                    } else if (resultNode.has("name")) {
                        title = resultNode.get("name").asText();
                    }

                    // URL字段
                    if (resultNode.has("url")) {
                        url = resultNode.get("url").asText();
                    } else if (resultNode.has("link")) {
                        url = resultNode.get("link").asText();
                    } else if (resultNode.has("href")) {
                        url = resultNode.get("href").asText();
                    }

                    // 内容字段
                    if (resultNode.has("content")) {
                        content = resultNode.get("content").asText();
                    } else if (resultNode.has("snippet")) {
                        content = resultNode.get("snippet").asText();
                    } else if (resultNode.has("description")) {
                        content = resultNode.get("description").asText();
                    } else if (resultNode.has("text")) {
                        content = resultNode.get("text").asText();
                    }

                    // 验证结果的有效性
                    if (url != null && !url.isEmpty() &&
                        !url.contains("javascript:") &&
                        !url.startsWith("#") &&
                        title != null && !title.isEmpty() &&
                        title.length() >= 3) {

                        // 检查结果相关性（过滤明显不相关的结果）
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

            // 如果结果不相关，记录警告
            if (results.size() > 0 && skipped == 0) {
                // 检查前几个结果是否相关（简单启发式：检查标题和URL是否包含查询关键词）
                boolean hasRelevantResult = false;
                String lowerQuery = query.toLowerCase();
                // 提取查询中的关键词（去除常见词）
                String[] queryKeywords = lowerQuery.split("\\s+");
                for (SearchResult result : results) {
                    String title = result.getTitle() != null ? result.getTitle().toLowerCase() : "";
                    String url = result.getUrl() != null ? result.getUrl().toLowerCase() : "";
                    for (String keyword : queryKeywords) {
                        if (keyword.length() > 2 && (title.contains(keyword) || url.contains(keyword))) {
                            hasRelevantResult = true;
                            break;
                        }
                    }
                    if (hasRelevantResult) break;
                }
                if (!hasRelevantResult) {
                    logger.warn("搜索结果可能不相关 - 查询: {}, 结果标题: {}",
                            query, results.stream()
                                    .map(SearchResult::getTitle)
                                    .limit(3)
                                    .collect(java.util.stream.Collectors.joining(", ")));
                }
            }

        } catch (Exception e) {
            logger.error("解析SearX-NG搜索结果失败", e);
            // 如果JSON解析失败，记录原始响应的一部分用于调试
            String jsonPreview = jsonResponse.length() > 1000 ? jsonResponse.substring(0, 1000) : jsonResponse;
            logger.warn("JSON响应预览（解析失败）: {}", jsonPreview);
        }

        return results;
    }

    /**
     * 检查搜索结果是否与查询相关（通用相关性检查，适用于所有查询类型）
     * @param title 结果标题
     * @param url 结果URL
     * @param content 结果内容
     * @param query 原始查询
     * @return true表示相关，false表示不相关
     */
    private boolean isResultRelevant(String title, String url, String content, String query) {
        if (title == null || url == null || query == null) {
            return true; // 如果信息不完整，默认认为相关，避免过度过滤
        }

        String lowerTitle = title.toLowerCase();
        String lowerUrl = url.toLowerCase();
        String lowerContent = content != null ? content.toLowerCase() : "";
        String lowerQuery = query.toLowerCase();

        // 提取查询中的关键词（去除常见停用词和标点符号）
        String[] stopWords = {"的", "了", "在", "是", "有", "和", "与", "或", "但", "就", "都", "也", "还", "什么", "怎么", "如何",
                             "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "what", "how"};

        // 使用更智能的方式提取关键词：支持中英文混合
        java.util.List<String> keywords = new java.util.ArrayList<>();

        // 对于中文查询，提取连续的中文字符作为关键词（优先处理，因为中文词更重要）
        java.util.regex.Pattern chinesePattern = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}");
        java.util.regex.Matcher chineseMatcher = chinesePattern.matcher(lowerQuery);
        while (chineseMatcher.find()) {
            String chineseWord = chineseMatcher.group();
            if (chineseWord.length() >= 2 && !java.util.Arrays.asList(stopWords).contains(chineseWord)) {
                keywords.add(chineseWord);
            }
        }

        // 再按空格分割提取英文关键词
        String[] queryWords = lowerQuery.split("\\s+");
        for (String word : queryWords) {
            word = word.trim().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", ""); // 移除标点符号
            if (word.length() > 1 && !java.util.Arrays.asList(stopWords).contains(word) && !keywords.contains(word)) {
                keywords.add(word);
            }
        }

        // 如果查询关键词太少，不进行过滤（避免过度过滤）
        if (keywords.size() < 1) {
            return true;
        }

        // 检查标题、URL或内容中是否包含查询关键词
        int matchCount = 0;
        int importantMatchCount = 0; // 重要关键词匹配数（地名、实体等）

        for (String keyword : keywords) {
            if (keyword.length() >= 2) {
                boolean matched = lowerTitle.contains(keyword) || lowerUrl.contains(keyword) || lowerContent.contains(keyword);
                if (matched) {
                    matchCount++;
                    // 如果关键词长度>=3或者是中文，认为是重要关键词
                    if (keyword.length() >= 3 || keyword.matches("[\\u4e00-\\u9fa5]+")) {
                        importantMatchCount++;
                    }
                }
            }
        }

        // 相关性判断：必须满足以下条件之一
        // 1. 至少匹配了50%的关键词（提高阈值，更严格）
        // 2. 至少匹配了2个重要关键词（长度>=3或中文）且匹配了至少40%的关键词
        // 3. 如果只有1-2个关键词，必须全部匹配
        double relevanceRatio = keywords.size() > 0 ? (double) matchCount / keywords.size() : 0.0;
        boolean isRelevant;

        if (keywords.size() <= 2) {
            // 关键词很少时，要求全部匹配
            isRelevant = matchCount == keywords.size();
        } else {
            // 关键词较多时，使用更严格的阈值
            isRelevant = relevanceRatio >= 0.5 || (importantMatchCount >= 2 && relevanceRatio >= 0.4);
        }

        if (!isRelevant) {
            logger.info("结果不相关 - 标题: '{}', 匹配关键词数: {}/{}, 重要关键词匹配: {}, 相关性: {:.2f}, 查询: '{}'",
                    title, matchCount, keywords.size(), importantMatchCount, relevanceRatio, query);
            logger.info("查询关键词: {}", keywords);
            logger.info("结果文本: 标题='{}', URL='{}', 内容='{}'",
                    title, url, content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
        }

        return isRelevant;
    }

    /**
     * 将搜索结果格式化为文本，用于LLM上下文
     */
    public String formatSearchResultsForContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        // 获取当前日期，用于时效性检查
        java.time.LocalDate currentDate = java.time.LocalDate.now();
        String currentDateStr = currentDate.toString();
        int currentYear = currentDate.getYear();

        StringBuilder sb = new StringBuilder();
        sb.append("【网络搜索结果】\n");
        sb.append("以下是通过网络搜索找到的相关信息（搜索时间：").append(currentDateStr).append("），请仔细阅读并用于回答用户的问题：\n\n");
        sb.append("【重要提示】请特别注意信息的时效性：\n");
        sb.append("1. 如果搜索结果中的信息包含日期，请检查日期是否为最新（当前年份：").append(currentYear).append("年）\n");
        sb.append("2. 如果搜索结果中的信息明显过期（如日期是2023年或更早），请明确告知用户信息可能已过期\n");
        sb.append("3. 优先使用包含最新日期的搜索结果\n");
        sb.append("4. 如果所有搜索结果都是过期的，请明确说明\"搜索结果中的信息可能已过期，建议访问相关网站获取最新信息\"\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);
            sb.append(String.format("搜索结果 %d：\n", i + 1));
            sb.append(String.format("标题：%s\n", result.getTitle()));
            if (result.getUrl() != null && !result.getUrl().isEmpty()) {
                sb.append(String.format("来源：%s\n", result.getUrl()));
            }
            if (result.getSnippet() != null && !result.getSnippet().isEmpty()) {
                sb.append(String.format("内容摘要：%s\n", result.getSnippet()));
            } else {
                // 如果没有摘要，至少提供标题信息
                sb.append(String.format("内容：关于\"%s\"的相关信息\n", result.getTitle()));
            }
            sb.append("\n");
        }

        sb.append("---\n");
        sb.append("请基于以上搜索结果来回答用户的问题。\n");
        sb.append("【关键要求】\n");
        sb.append("1. 如果搜索结果中包含相关信息，必须优先使用搜索结果中的内容，并在回答中明确标注来源链接\n");
        sb.append("2. 如果搜索结果中的信息包含日期，请检查日期是否为最新（当前年份：").append(currentYear).append("年）\n");
        sb.append("3. 如果搜索结果中的信息明显过期（日期是2023年或更早），必须在回答开头明确说明\"注意：以下信息可能已过期\"，并建议用户访问相关网站获取最新信息\n");
        sb.append("4. 如果所有搜索结果都是过期的，请明确告知用户\"搜索结果中的信息可能已过期，建议访问相关官方网站或新闻网站获取最新信息\"\n");

        return sb.toString();
    }

    /**
     * 搜索结果数据类
     */
    public static class SearchResult {
        private String title;
        private String url;
        private String snippet;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}
