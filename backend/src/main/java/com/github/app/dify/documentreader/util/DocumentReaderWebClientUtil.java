package com.github.app.dify.documentreader.util;

import com.github.app.dify.common.util.WebClientFactoryUtil;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 文档解读 WebClient 工具类
 * 提供统一的 WebClient 创建和配置方法
 */
public class DocumentReaderWebClientUtil {
    
    /**
     * 创建默认的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilder(String baseUrl) {
        return WebClientFactoryUtil.createBuilder(baseUrl);
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
        return WebClientFactoryUtil.createHttpClient(timeoutSeconds);
    }
    
    /**
     * 创建带超时和连接超时配置的 HttpClient
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @param connectTimeoutMs 连接超时时间（毫秒）
     * @return HttpClient
     */
    public static HttpClient createHttpClient(int timeoutSeconds, int connectTimeoutMs) {
        return WebClientFactoryUtil.createHttpClient(timeoutSeconds, connectTimeoutMs);
    }
}
