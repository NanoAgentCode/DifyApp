package com.github.app.dify.knowledgebase.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 知识库 WebClient 工具类
 * 提供统一的 WebClient 创建和配置方法
 */
public class KnowledgeBaseWebClientUtil {
    
    // 默认超时时间
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;
    
    // 默认缓冲区大小（10MB）
    private static final int DEFAULT_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    
    // 大批量操作缓冲区大小（50MB）
    private static final int LARGE_MAX_IN_MEMORY_SIZE = 50 * 1024 * 1024;
    
    /**
     * 创建默认的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilder(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(DEFAULT_MAX_IN_MEMORY_SIZE));
    }
    
    /**
     * 创建带 API Key 认证的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @param apiKey API Key
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderWithApiKey(String baseUrl, String apiKey) {
        WebClient.Builder builder = createBuilder(baseUrl);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return builder;
    }
    
    /**
     * 创建带自定义认证头的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @param apiKey API Key
     * @param authHeaderName 认证头名称（如 "X-API-Key"）
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderWithCustomAuth(String baseUrl, String apiKey, String authHeaderName) {
        WebClient.Builder builder = createBuilder(baseUrl);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(authHeaderName, apiKey);
        }
        return builder;
    }
    
    /**
     * 创建大批量操作的 WebClient Builder（50MB 缓冲区）
     * 
     * @param baseUrl 基础URL
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderForLargeOperations(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(LARGE_MAX_IN_MEMORY_SIZE));
    }
    
    /**
     * 创建带超时配置的 HttpClient
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return HttpClient
     */
    public static HttpClient createHttpClient(int timeoutSeconds) {
        return createHttpClient(timeoutSeconds, DEFAULT_CONNECT_TIMEOUT_MS);
    }
    
    /**
     * 创建带超时和连接超时配置的 HttpClient
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @param connectTimeoutMs 连接超时时间（毫秒）
     * @return HttpClient
     */
    public static HttpClient createHttpClient(int timeoutSeconds, int connectTimeoutMs) {
        return HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
    }
    
    /**
     * 创建带 HttpClient 和 API Key 的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @param apiKey API Key
     * @param httpClient HttpClient
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderWithHttpClient(String baseUrl, String apiKey, HttpClient httpClient) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(DEFAULT_MAX_IN_MEMORY_SIZE));
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        return builder;
    }
    
    /**
     * 创建大批量操作带 HttpClient 和 API Key 的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @param apiKey API Key
     * @param httpClient HttpClient
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderForLargeOperationsWithHttpClient(String baseUrl, String apiKey, HttpClient httpClient) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(LARGE_MAX_IN_MEMORY_SIZE));
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        
        return builder;
    }
}

