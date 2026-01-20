package com.github.app.dify.common.config;

import com.github.app.dify.system.config.DifyConfig;
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
import reactor.netty.resources.ConnectionProvider;

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
    
    // 非流式响应超时时间（毫秒）- 至少5分钟
    private static final long DEFAULT_RESPONSE_TIMEOUT = Math.max(300000L, 30000L);
    
    // 流式响应超时时间（毫秒）- 至少10分钟
    private static final long DEFAULT_STREAMING_TIMEOUT = Math.max(600000L, 30000L);
    
    // 最大内存缓冲区大小（10MB）
    private static final int MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    
    // 连接池配置参数
    private static final int MAX_CONNECTIONS = 500;                    // 最大连接数
    private static final int MAX_IDLE_TIME_SECONDS = 20;               // 空闲连接超时（秒）
    private static final int MAX_LIFE_TIME_MINUTES = 5;                // 连接最大生命周期（分钟）
    private static final int PENDING_ACQUIRE_TIMEOUT_SECONDS = 60;      // 获取连接超时（秒）
    private static final int EVICT_IN_BACKGROUND_SECONDS = 120;        // 后台清理间隔（秒）
    
    // 流式连接池配置（通常需要更多连接，因为连接保持时间更长）
    private static final int STREAMING_MAX_CONNECTIONS = 200;           // 流式最大连接数
    private static final int STREAMING_MAX_IDLE_TIME_SECONDS = 60;     // 流式空闲连接超时（秒）
    private static final int STREAMING_MAX_LIFE_TIME_MINUTES = 10;     // 流式连接最大生命周期（分钟）
    
    // 连接池提供者（单例，避免重复创建）
    private static volatile ConnectionProvider defaultConnectionProvider;
    private static volatile ConnectionProvider streamingConnectionProvider;
    
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
        
        // 获取或创建连接池提供者
        ConnectionProvider connectionProvider = getConnectionProvider(isStreaming);
        
        logger.info("创建WebClient - URL: {}, 连接超时: {}ms, 响应超时: {}ms, 流式: {}, 连接池: {}", 
                baseUrl, connectTimeout, responseTimeout, isStreaming, 
                isStreaming ? "streaming" : "default");
        
        // 配置HttpClient（使用连接池）
        HttpClient httpClient = HttpClient.create(connectionProvider)
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
     * 获取或创建连接池提供者（单例模式，避免重复创建）
     * 
     * @param isStreaming 是否为流式连接池
     * @return ConnectionProvider实例
     */
    private ConnectionProvider getConnectionProvider(boolean isStreaming) {
        if (isStreaming) {
            // 流式连接池（双检锁单例模式）
            if (streamingConnectionProvider == null) {
                synchronized (WebClientConfig.class) {
                    if (streamingConnectionProvider == null) {
                        streamingConnectionProvider = ConnectionProvider.builder("streaming-pool")
                                .maxConnections(STREAMING_MAX_CONNECTIONS)
                                .maxIdleTime(Duration.ofSeconds(STREAMING_MAX_IDLE_TIME_SECONDS))
                                .maxLifeTime(Duration.ofMinutes(STREAMING_MAX_LIFE_TIME_MINUTES))
                                .pendingAcquireTimeout(Duration.ofSeconds(PENDING_ACQUIRE_TIMEOUT_SECONDS))
                                .evictInBackground(Duration.ofSeconds(EVICT_IN_BACKGROUND_SECONDS))
                                .build();
                        logger.info("创建流式连接池 - 最大连接数: {}, 空闲超时: {}秒, 最大生命周期: {}分钟", 
                                STREAMING_MAX_CONNECTIONS, STREAMING_MAX_IDLE_TIME_SECONDS, STREAMING_MAX_LIFE_TIME_MINUTES);
                    }
                }
            }
            return streamingConnectionProvider;
        } else {
            // 默认连接池（双检锁单例模式）
            if (defaultConnectionProvider == null) {
                synchronized (WebClientConfig.class) {
                    if (defaultConnectionProvider == null) {
                        defaultConnectionProvider = ConnectionProvider.builder("default-pool")
                                .maxConnections(MAX_CONNECTIONS)
                                .maxIdleTime(Duration.ofSeconds(MAX_IDLE_TIME_SECONDS))
                                .maxLifeTime(Duration.ofMinutes(MAX_LIFE_TIME_MINUTES))
                                .pendingAcquireTimeout(Duration.ofSeconds(PENDING_ACQUIRE_TIMEOUT_SECONDS))
                                .evictInBackground(Duration.ofSeconds(EVICT_IN_BACKGROUND_SECONDS))
                                .build();
                        logger.info("创建默认连接池 - 最大连接数: {}, 空闲超时: {}秒, 最大生命周期: {}分钟", 
                                MAX_CONNECTIONS, MAX_IDLE_TIME_SECONDS, MAX_LIFE_TIME_MINUTES);
                    }
                }
            }
            return defaultConnectionProvider;
        }
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
