package com.github.app.dify.common.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebClient配置类
 * 管理WebClient实例的创建和复用，避免重复创建连接
 */
@Configuration
public class WebClientConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);
    
    @Autowired
    private DifyConfig difyConfig;
    
    // 缓存不同base URL的WebClient实例（非流式）
    private final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();
    
    // 缓存不同base URL的WebClient实例（流式）
    private final Map<String, WebClient> streamingWebClientCache = new ConcurrentHashMap<>();
    
    // 默认连接超时时间（毫秒）
    private static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    
    // 非流式响应超时时间（毫秒）- 至少5分钟
    private static final long DEFAULT_RESPONSE_TIMEOUT = Math.max(300000L, 30000L);
    
    // 流式响应超时时间（毫秒）- 至少10分钟
    private static final long DEFAULT_STREAMING_TIMEOUT = Math.max(600000L, 30000L);
    
    // 最大内存缓冲区大小（10MB）
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    
    /**
     * 获取或创建WebClient实例（非流式）
     * 
     * @param baseUrl 基础URL，如果为null则使用默认配置
     * @return WebClient实例
     */
    public WebClient getWebClient(String baseUrl) {
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        
        return webClientCache.computeIfAbsent(normalizedUrl, url -> {
            logger.info("创建新的非流式WebClient实例，URL: {}", url);
            return createWebClientInternal(url, false);
        });
    }
    
    /**
     * 获取或创建WebClient实例（流式）
     * 
     * @param baseUrl 基础URL，如果为null则使用默认配置
     * @return WebClient实例
     */
    public WebClient getStreamingWebClient(String baseUrl) {
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        
        return streamingWebClientCache.computeIfAbsent(normalizedUrl, url -> {
            logger.info("创建新的流式WebClient实例，URL: {}", url);
            return createWebClientInternal(url, true);
        });
    }
    
    /**
     * 规范化基础URL（移除尾部斜杠）
     */
    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl;
        
        if (url == null || url.trim().isEmpty()) {
            url = difyConfig.getDefaultBaseUrl();
        }
        
        url = url.trim();
        
        // 移除尾随斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        
        return url;
    }
    
    /**
     * 创建WebClient实例
     * 
     * @param baseUrl 基础URL
     * @param isStreaming 是否为流式响应
     * @return WebClient实例
     */
    private WebClient createWebClientInternal(String baseUrl, boolean isStreaming) {
        int connectTimeout = difyConfig.getConnectTimeout();
        long responseTimeout = isStreaming ? DEFAULT_STREAMING_TIMEOUT : DEFAULT_RESPONSE_TIMEOUT;
        
        logger.info("创建WebClient - URL: {}, 连接超时: {}ms, 响应超时: {}ms, 流式: {}", 
                baseUrl, connectTimeout, responseTimeout, isStreaming);
        
        // 配置HttpClient
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                // 添加读写超时处理器
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(responseTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(responseTimeout, TimeUnit.MILLISECONDS)));
        
        // 创建WebClient.Builder
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(org.springframework.http.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE));
        
        // 为流式WebClient添加SSE接受类型
        if (isStreaming) {
            builder.defaultHeader(org.springframework.http.HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        }
        
        return builder.build();
    }
    
    /**
     * 清除缓存的WebClient实例
     * 用于配置更新或需要重建连接时
     */
    public void clearCache() {
        logger.info("清除所有缓存的WebClient实例");
        webClientCache.clear();
        streamingWebClientCache.clear();
    }
    
    /**
     * 清除指定URL的WebClient实例
     * 
     * @param baseUrl 要清除的基础URL
     */
    public void clearCacheForUrl(String baseUrl) {
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        logger.info("清除指定URL的WebClient实例: {}", normalizedUrl);
        webClientCache.remove(normalizedUrl);
        streamingWebClientCache.remove(normalizedUrl);
    }
    
    /**
     * 获取缓存统计信息
     * 
     * @return 包含缓存大小的Map
     */
    public Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("webClientCount", webClientCache.size());
        stats.put("streamingWebClientCount", streamingWebClientCache.size());
        return stats;
    }
    
    /**
     * 创建默认的WebClient Bean（用于非Dify API调用）
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public WebClient defaultWebClient() {
        return getWebClient(null);
    }
}
