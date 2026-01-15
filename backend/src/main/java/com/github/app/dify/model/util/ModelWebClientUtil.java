package com.github.app.dify.model.util;

import com.github.app.dify.common.util.WebClientFactoryUtil;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 模型 WebClient 工具类
 * 提供统一的 WebClient 创建和配置方法
 */
public class ModelWebClientUtil {
    
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
     * 创建带 API Key 认证的 WebClient Builder
     * 
     * @param baseUrl 基础URL
     * @param apiKey API Key
     * @return WebClient.Builder
     */
    public static WebClient.Builder createBuilderWithApiKey(String baseUrl, String apiKey) {
        return WebClientFactoryUtil.withBearerAuth(createBuilder(baseUrl), apiKey);
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
