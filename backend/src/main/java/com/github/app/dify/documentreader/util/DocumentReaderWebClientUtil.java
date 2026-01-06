package com.github.app.dify.documentreader.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 文档解读 WebClient 工具类
 * 提供统一的 WebClient 创建和配置方法
 */
public class DocumentReaderWebClientUtil {
    
    // 默认超时时间
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;
    
    // 默认缓冲区大小（10MB）
    private static final int DEFAULT_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;
    
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
     * 创建带超时配置的 WebClient
     * 
     * @param baseUrl 基础URL
     * @param timeoutSeconds 超时时间（秒）
     * @return WebClient 实例
     */
    public static WebClient createWithTimeout(String baseUrl, int timeoutSeconds) {
        return createBuilder(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(timeoutSeconds)))
                .build();
    }
    
    /**
     * 创建带超时和连接超时配置的 WebClient
     * 
     * @param baseUrl 基础URL
     * @param timeoutSeconds 超时时间（秒）
     * @param connectTimeoutMs 连接超时时间（毫秒）
     * @return WebClient 实例
     */
    public static WebClient createWithTimeouts(String baseUrl, int timeoutSeconds, int connectTimeoutMs) {
        return createBuilder(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(createHttpClient(timeoutSeconds, connectTimeoutMs)))
                .build();
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
}

