package com.github.app.dify.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.config.EmbeddingConfig;
import com.github.app.dify.config.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化服务
 */
@Service
public class EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    @Autowired
    private EmbeddingConfig embeddingConfig;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private WebClient webClient;
    
    /**
     * 根据 provider 类型构建完整的 API URL
     */
    private String buildApiUrl() {
        String apiUrl = embeddingConfig.getApiUrl();
        ProviderType providerType = ProviderType.fromValue(embeddingConfig.getProvider());
        
        // 如果 URL 已经包含路径（包含 /api/ 或 /v1/），则直接使用
        if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            return apiUrl;
        }
        
        // 根据 provider 类型添加相应的路径
        String path = providerType.getEmbeddingPath();
        return apiUrl.endsWith("/") ? apiUrl + path.substring(1) : apiUrl + path;
    }
    
    private WebClient getWebClient() {
        if (webClient == null) {
            String baseUrl = buildApiUrl();
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    // 增加缓冲区大小以支持大响应（默认256KB，增加到50MB以支持大批量向量化）
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));
            
            // 根据 provider 类型决定是否需要 API Key
            ProviderType providerType = ProviderType.fromValue(embeddingConfig.getProvider());
            if (providerType.requiresApiKey() && embeddingConfig.getApiKey() != null && !embeddingConfig.getApiKey().trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + embeddingConfig.getApiKey());
            }
            
            webClient = builder.build();
            logger.info("EmbeddingService WebClient已创建，Provider: {}, URL: {}, 缓冲区大小: 50MB", 
                    providerType.getValue(), baseUrl);
        }
        return webClient;
    }
    
    /**
     * 向量化单个文本
     */
    public List<Float> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        List<String> texts = new ArrayList<>();
        texts.add(text);
        List<List<Float>> embeddings = embedBatch(texts);
        return embeddings.isEmpty() ? new ArrayList<>() : embeddings.get(0);
    }
    
    /**
     * 批量向量化
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", texts);
            if (embeddingConfig.getModel() != null && !embeddingConfig.getModel().trim().isEmpty()) {
                requestBody.put("model", embeddingConfig.getModel());
            }
            
            String apiUrl = buildApiUrl();
            logger.debug("调用向量化API - Provider: {}, URL: {}, 文本数量: {}", 
                    embeddingConfig.getProvider(), apiUrl, texts.size());
            
            String response = getWebClient()
                    .post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(embeddingConfig.getTimeout()))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(throwable -> {
                                if (throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                    org.springframework.web.reactive.function.client.WebClientResponseException ex = 
                                            (org.springframework.web.reactive.function.client.WebClientResponseException) throwable;
                                    // 只重试5xx错误，不重试4xx错误
                                    return ex.getStatusCode().is5xxServerError();
                                }
                                return true;
                            }))
                    .block();
            
            if (response == null || response.trim().isEmpty()) {
                throw new RuntimeException("向量化API返回空响应");
            }
            
            // 解析响应
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
            
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("向量化API返回数据为空");
            }
            
            List<List<Float>> embeddings = new ArrayList<>();
            for (Map<String, Object> item : data) {
                List<Object> embeddingObj = (List<Object>) item.get("embedding");
                if (embeddingObj != null) {
                    List<Float> embedding = new ArrayList<>();
                    for (Object value : embeddingObj) {
                        if (value instanceof Number) {
                            embedding.add(((Number) value).floatValue());
                        }
                    }
                    embeddings.add(embedding);
                }
            }
            
            logger.debug("向量化成功 - 文本数量: {}, 向量维度: {}", 
                    embeddings.size(), 
                    embeddings.isEmpty() ? 0 : embeddings.get(0).size());
            
            return embeddings;
            
        } catch (Exception e) {
            logger.error("向量化失败", e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 分批向量化（处理大量文本）
     */
    public List<List<Float>> embedBatchWithChunking(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        int batchSize = embeddingConfig.getBatchSize();
        List<List<Float>> allEmbeddings = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            try {
                List<List<Float>> batchEmbeddings = embedBatch(batch);
                allEmbeddings.addAll(batchEmbeddings);
                
                // 避免请求过快
                if (i + batchSize < texts.size()) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                logger.error("批量向量化失败 - 批次: {}-{}", i, end, e);
                throw new RuntimeException("批量向量化失败: " + e.getMessage(), e);
            }
        }
        
        return allEmbeddings;
    }
}

