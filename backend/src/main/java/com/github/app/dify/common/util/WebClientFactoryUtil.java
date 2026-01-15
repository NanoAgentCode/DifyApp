package com.github.app.dify.common.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

public class WebClientFactoryUtil {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30000;
    public static final int DEFAULT_MAX_IN_MEMORY_SIZE = 10 * 1024 * 1024;

    public static WebClient.Builder createBuilder(String baseUrl) {
        return createBuilder(baseUrl, DEFAULT_MAX_IN_MEMORY_SIZE);
    }

    public static WebClient.Builder createBuilder(String baseUrl, int maxInMemorySize) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(maxInMemorySize));
    }

    public static WebClient.Builder createBuilder(String baseUrl, HttpClient httpClient) {
        return createBuilder(baseUrl, httpClient, DEFAULT_MAX_IN_MEMORY_SIZE);
    }

    public static WebClient.Builder createBuilder(String baseUrl, HttpClient httpClient, int maxInMemorySize) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(maxInMemorySize));
    }

    public static WebClient.Builder withBearerAuth(WebClient.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return builder;
    }

    public static WebClient.Builder withHeader(WebClient.Builder builder, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            builder.defaultHeader(name, value);
        }
        return builder;
    }

    public static HttpClient createHttpClient(int timeoutSeconds) {
        return createHttpClient(timeoutSeconds, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public static HttpClient createHttpClient(int timeoutSeconds, int connectTimeoutMs) {
        return HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs);
    }
}

