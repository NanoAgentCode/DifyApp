package com.github.app.dify.knowledgebase.service.strategy;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Reuses per-knowledge-base WebClient instances while recreating them when a
 * vector database endpoint or API key changes.
 */
abstract class AbstractHttpVectorStoreStrategy {

    private final ConcurrentHashMap<Long, CachedClient> webClientCache = new ConcurrentHashMap<>();

    protected WebClient getCachedWebClient(Long knowledgeBaseId, String url, String apiKey,
            String apiKeyHeader, Consumer<WebClient.Builder> customizer) {
        CachedClient cachedClient = webClientCache.get(knowledgeBaseId);
        if (cachedClient != null && cachedClient.matches(url, apiKey)) {
            return cachedClient.client();
        }

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        customizer.accept(builder);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(apiKeyHeader, apiKeyHeader.equals(HttpHeaders.AUTHORIZATION) ? "Bearer " + apiKey : apiKey);
        }

        WebClient client = builder.build();
        webClientCache.put(knowledgeBaseId, new CachedClient(client, url, apiKey));
        return client;
    }

    private record CachedClient(WebClient client, String url, String apiKey) {
        private boolean matches(String currentUrl, String currentApiKey) {
            return Objects.equals(url, currentUrl) && Objects.equals(apiKey, currentApiKey);
        }
    }
}
