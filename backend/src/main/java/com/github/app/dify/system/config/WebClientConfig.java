package com.github.app.dify.system.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 为 Dify API 等提供 WebClient 实例（按 baseUrl 缓存）
 */
@Component
public class WebClientConfig {

    private final ConcurrentHashMap<String, WebClient> webClientCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebClient> streamingWebClientCache = new ConcurrentHashMap<>();

    public WebClient getWebClient(String baseUrl) {
        String key = baseUrl == null ? "" : baseUrl.trim();
        return webClientCache.computeIfAbsent(key, this::buildWebClient);
    }

    public WebClient getStreamingWebClient(String baseUrl) {
        String key = baseUrl == null ? "" : baseUrl.trim();
        return streamingWebClientCache.computeIfAbsent(key, this::buildStreamingWebClient);
    }

    private WebClient buildWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private WebClient buildStreamingWebClient(String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
