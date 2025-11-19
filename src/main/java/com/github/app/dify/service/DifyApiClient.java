package com.github.app.dify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.DifyConfig;
import com.github.app.dify.resp.DifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import io.netty.channel.ChannelOption;
import reactor.util.function.Tuples;
import reactor.util.function.Tuple2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
        String url = (baseUrl != null && !baseUrl.trim().isEmpty()) 
                ? baseUrl.trim() 
                : difyConfig.getDefaultBaseUrl();
        // 移除尾随斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        logger.info("使用Dify API Base URL: {}", url);
        
        // 配置HTTP客户端超时（用于非流式响应）
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(difyConfig.getTimeout()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, difyConfig.getConnectTimeout());
        
        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    /**
     * 创建用于流式响应的WebClient（不设置responseTimeout，超时由Flux级别控制）
     */
    private WebClient createStreamWebClient(String baseUrl) {
        String url = (baseUrl != null && !baseUrl.trim().isEmpty()) 
                ? baseUrl.trim() 
                : difyConfig.getDefaultBaseUrl();
        // 移除尾随斜杠
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        logger.info("使用Dify API Base URL (流式): {}", url);
        
        // 配置HTTP客户端（仅设置连接超时，不设置响应超时，由Flux级别控制）
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, difyConfig.getConnectTimeout());
        
        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    /**
     * 调用Chat Flow API（非流式）
     */
    public Mono<DifyResponse> chat(String apiKey, String baseUrl, String query, String conversationId, 
                                    String userId, Map<String, Object> inputs) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key不能为空");
        }
        
        WebClient webClient = createWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", inputs != null ? inputs : new java.util.HashMap<>());
        requestBody.put("response_mode", "blocking"); // Dify非流式响应模式
        requestBody.put("stream", false);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Chat API, URL: {}, API Key: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
        
        // Dify API路径
        String apiPath = "/v1/chat-messages";
        
        return webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim())
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
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        logger.error("调用Dify Chat API失败: {} {}, 响应: {}", 
                            ex.getStatusCode(), ex.getStatusText(), ex.getResponseBodyAsString());
                    } else {
                        logger.error("调用Dify Chat API失败", error);
                    }
                });
    }
    
    /**
     * 调用Chat Flow API（流式）
     */
    public Flux<DifyResponse> chatStream(String apiKey, String baseUrl, String query, String conversationId,
                                          String userId, Map<String, Object> inputs) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key不能为空");
        }
        
        WebClient webClient = createStreamWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            requestBody.put("conversation_id", conversationId);
        }
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", inputs != null ? inputs : new java.util.HashMap<>());
        requestBody.put("response_mode", "streaming"); // Dify流式响应模式
        requestBody.put("stream", true);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Chat Stream API, URL: {}, API Key: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
        
        // Dify API路径
        String apiPath = "/v1/chat-messages";
        
        // 对于流式响应，使用更长的超时时间（5分钟）
        long streamTimeout = Math.max(difyConfig.getTimeout(), 300000); // 至少5分钟
        
        return webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .doOnSubscribe(subscription -> {
                    logger.info("开始订阅Dify Chat Stream API响应");
                })
                .doOnNext(dataBuffer -> {
                    logger.info("收到数据块，大小: {} 字节", dataBuffer.readableByteCount());
                })
                .timeout(Duration.ofMillis(streamTimeout))
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .scan(Tuples.<String, List<String>>of("", new ArrayList<String>()), (state, chunk) -> {
                    // state: Tuple2<buffer, lines>
                    String buffer = state.getT1();
                    
                    // 累积新数据
                    String accumulated = buffer + chunk;
                    
                    // 提取完整的行
                    List<String> newLines = new ArrayList<>();
                    int lastNewline = accumulated.lastIndexOf('\n');
                    if (lastNewline >= 0) {
                        String completePart = accumulated.substring(0, lastNewline + 1);
                        String[] splitLines = completePart.split("\n");
                        for (String line : splitLines) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                newLines.add(trimmed);
                            }
                        }
                        // 保留不完整的部分
                        buffer = accumulated.substring(lastNewline + 1);
                    } else {
                        // 没有完整的行，保留全部
                        buffer = accumulated;
                    }
                    
                    return Tuples.of(buffer, newLines);
                })
                .skip(1) // 跳过初始状态
                .flatMap(state -> {
                    List<String> lines = state.getT2();
                    if (lines.isEmpty()) {
                        return Flux.<String>empty();
                    }
                    logger.debug("处理完整行: {} 行", lines.size());
                    return Flux.fromIterable(lines);
                })
                .flatMap(line -> {
                    try {
                        logger.info("收到SSE数据行: {}", line.length() > 200 ? line.substring(0, 200) + "..." : line);
                        if (line == null || line.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空行
                        }
                        // 处理SSE格式的数据
                        String jsonData = line;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6).trim();
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            logger.info("收到流式响应结束标记");
                            return Mono.just(response);
                        }
                        if (jsonData.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空数据
                        }
                        DifyResponse response = objectMapper.readValue(jsonData, DifyResponse.class);
                        logger.info("成功解析Dify响应: event={}, answer长度={}, finished={}", 
                                response.getEvent(), 
                                response.getAnswer() != null ? response.getAnswer().length() : 0,
                                response.getFinished());
                        return Mono.just(response);
                    } catch (Exception e) {
                        logger.warn("解析Dify流式响应失败，跳过该行: {}, 错误: {}", 
                                line.length() > 200 ? line.substring(0, 200) + "..." : line, e.getMessage());
                        logger.debug("解析错误详情", e);
                        // 跳过解析失败的行，返回空 Mono
                        return Mono.<DifyResponse>empty();
                    }
                })
                .doOnNext(response -> {
                    logger.info("发送Dify响应: event={}, finished={}", response.getEvent(), response.getFinished());
                })
                .doOnComplete(() -> {
                    logger.info("Dify Chat Stream API响应流完成");
                })
                .doOnCancel(() -> {
                    logger.warn("Dify Chat Stream API响应流被取消");
                })
                .doOnError(error -> {
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        logger.error("调用Dify Chat Stream API超时: 超时时间 {} 毫秒, URL: {}", streamTimeout, actualUrl);
                    } else if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Chat Stream API失败: {} {}, URL: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, responseBody);
                        } catch (Exception e) {
                            logger.error("调用Dify Chat Stream API失败: {} {}, URL: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl);
                        }
                    } else {
                        logger.error("调用Dify Chat Stream API失败, URL: " + actualUrl, error);
                    }
                });
    }
    
    /**
     * 调用Workflow API（非流式）
     */
    public Mono<DifyResponse> workflow(String apiKey, String baseUrl, String userId, Map<String, Object> inputs) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key不能为空");
        }
        
        WebClient webClient = createWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", inputs != null ? inputs : new java.util.HashMap<>());
        requestBody.put("response_mode", "blocking"); // Dify非流式响应模式
        requestBody.put("stream", false);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Workflow API, URL: {}, API Key: {}, 请求体: {}", 
                actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...", requestBody);
        
        // Dify API路径
        String apiPath = "/v1/workflows/run";
        
        return webClient.post()
                .uri(apiPath)
                .header("Authorization", "Bearer " + apiKey.trim())
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
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Workflow API失败: {} {}, URL: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, responseBody);
                        } catch (Exception e) {
                            logger.error("调用Dify Workflow API失败: {} {}, URL: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl);
                        }
                    } else {
                        logger.error("调用Dify Workflow API失败", error);
                    }
                });
    }
    
    /**
     * 调用Workflow API（流式）
     */
    public Flux<DifyResponse> workflowStream(String apiKey, String baseUrl, String userId, Map<String, Object> inputs) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("API Key不能为空");
        }
        
        WebClient webClient = createStreamWebClient(baseUrl);
        
        Map<String, Object> requestBody = new java.util.HashMap<>();
        if (userId != null && !userId.trim().isEmpty()) {
            requestBody.put("user", userId);
        }
        // inputs 是必需参数，即使为空也要包含
        requestBody.put("inputs", inputs != null ? inputs : new java.util.HashMap<>());
        requestBody.put("response_mode", "streaming"); // Dify流式响应模式
        requestBody.put("stream", true);
        
        String actualUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : difyConfig.getDefaultBaseUrl();
        logger.info("调用Dify Workflow Stream API, URL: {}, API Key: {}", actualUrl, apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
        
        return webClient.post()
                .uri("/v1/workflows/run")
                .header("Authorization", "Bearer " + apiKey.trim())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofMillis(difyConfig.getTimeout()))
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(chunk -> {
                    // 按行分割并处理每一行
                    List<String> lines = new ArrayList<>();
                    String[] splitLines = chunk.split("\n");
                    for (String line : splitLines) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            lines.add(trimmed);
                        }
                    }
                    return Flux.fromIterable(lines);
                })
                .flatMap(line -> {
                    try {
                        logger.debug("收到SSE数据行: {}", line);
                        if (line == null || line.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空行
                        }
                        // 处理SSE格式的数据
                        String jsonData = line;
                        if (jsonData.startsWith("data: ")) {
                            jsonData = jsonData.substring(6).trim();
                        }
                        if (jsonData.equals("[DONE]")) {
                            DifyResponse response = new DifyResponse();
                            response.setFinished(true);
                            logger.info("收到流式响应结束标记");
                            return Mono.just(response);
                        }
                        if (jsonData.isEmpty()) {
                            return Mono.<DifyResponse>empty(); // 跳过空数据
                        }
                        DifyResponse response = objectMapper.readValue(jsonData, DifyResponse.class);
                        logger.debug("成功解析Dify响应: event={}, answer长度={}", 
                                response.getEvent(), 
                                response.getAnswer() != null ? response.getAnswer().length() : 0);
                        return Mono.just(response);
                    } catch (Exception e) {
                        logger.warn("解析Dify流式响应失败，跳过该行: {}, 错误: {}", line, e.getMessage());
                        return Mono.<DifyResponse>empty(); // 跳过解析失败的行
                    }
                })
                .doOnNext(response -> {
                    logger.debug("发送Dify响应: event={}, finished={}", response.getEvent(), response.getFinished());
                })
                .doOnError(error -> {
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                            (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        try {
                            String responseBody = ex.getResponseBodyAsString();
                            logger.error("调用Dify Workflow Stream API失败: {} {}, URL: {}, 响应: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl, responseBody);
                        } catch (Exception e) {
                            logger.error("调用Dify Workflow Stream API失败: {} {}, URL: {}", 
                                ex.getStatusCode(), ex.getStatusText(), actualUrl);
                        }
                    } else {
                        logger.error("调用Dify Workflow Stream API失败", error);
                    }
                });
    }
}

