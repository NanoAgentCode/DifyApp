package com.github.app.dify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.DifyConfig;
import com.github.app.dify.resp.DifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Dify API客户端
 */
@Service
public class DifyApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DifyApiClient.class);
    
    @Autowired
    private DifyConfig difyConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private WebClient createWebClient(String baseUrl) {
        String url = baseUrl != null ? baseUrl : difyConfig.getDefaultBaseUrl();
        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    /**
     * 调用Chat Flow API（非流式）
     */
    public Mono<DifyResponse> chat(String apiKey, String baseUrl, String query, String conversationId, 
                                    String userId, Map<String, Object> inputs) {
        String url = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : difyConfig.getDefaultBaseUrl();
        WebClient webClient = createWebClient(url);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null) {
            requestBody.put("user", userId);
        }
        if (inputs != null) {
            requestBody.put("inputs", inputs);
        }
        requestBody.put("stream", false);
        
        return webClient.post()
                .uri("/v1/chat-messages")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .map(response -> {
                    try {
                        return objectMapper.readValue(response, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify响应失败", e);
                        throw new RuntimeException("解析Dify响应失败: " + e.getMessage(), e);
                    }
                })
                .doOnError(error -> logger.error("调用Dify Chat API失败", error));
    }
    
    /**
     * 调用Chat Flow API（流式）
     */
    public Flux<DifyResponse> chatStream(String apiKey, String baseUrl, String query, String conversationId,
                                          String userId, Map<String, Object> inputs) {
        String url = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : difyConfig.getDefaultBaseUrl();
        WebClient webClient = createWebClient(url);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null) {
            requestBody.put("user", userId);
        }
        if (inputs != null) {
            requestBody.put("inputs", inputs);
        }
        requestBody.put("stream", true);
        
        return webClient.post()
                .uri("/v1/chat-messages")
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .map(data -> {
                    try {
                        if (data == null || data.isEmpty()) {
                            return null;
                        }
                        // 处理SSE格式的数据
                        String jsonData = data;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6);
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            return response;
                        }
                        return objectMapper.readValue(jsonData, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify流式响应失败: " + data, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .doOnError(error -> logger.error("调用Dify Chat Stream API失败", error));
    }
    
    /**
     * 调用Workflow API（非流式）
     */
    public Mono<DifyResponse> workflow(String apiKey, String baseUrl, String userId, Map<String, Object> inputs) {
        String url = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : difyConfig.getDefaultBaseUrl();
        WebClient webClient = createWebClient(url);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (userId != null) {
            requestBody.put("user", userId);
        }
        if (inputs != null) {
            requestBody.put("inputs", inputs);
        }
        requestBody.put("stream", false);
        
        return webClient.post()
                .uri("/v1/workflows/run")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .map(response -> {
                    try {
                        return objectMapper.readValue(response, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify响应失败", e);
                        throw new RuntimeException("解析Dify响应失败: " + e.getMessage(), e);
                    }
                })
                .doOnError(error -> logger.error("调用Dify Workflow API失败", error));
    }
    
    /**
     * 调用Workflow API（流式）
     */
    public Flux<DifyResponse> workflowStream(String apiKey, String baseUrl, String userId, Map<String, Object> inputs) {
        String url = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : difyConfig.getDefaultBaseUrl();
        WebClient webClient = createWebClient(url);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (userId != null) {
            requestBody.put("user", userId);
        }
        if (inputs != null) {
            requestBody.put("inputs", inputs);
        }
        requestBody.put("stream", true);
        
        return webClient.post()
                .uri("/v1/workflows/run")
                .header("Authorization", "Bearer " + apiKey)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .map(data -> {
                    try {
                        if (data == null || data.isEmpty()) {
                            return null;
                        }
                        // 处理SSE格式的数据
                        String jsonData = data;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6);
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            return response;
                        }
                        return objectMapper.readValue(jsonData, DifyResponse.class);
                    } catch (Exception e) {
                        logger.error("解析Dify流式响应失败: " + data, e);
                        return null;
                    }
                })
                .filter(response -> response != null)
                .doOnError(error -> logger.error("调用Dify Workflow Stream API失败", error));
    }
}

