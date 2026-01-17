package com.github.app.dify.knowledgebase.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.system.config.ProviderType;
import com.github.app.dify.knowledgebase.domain.EmbeddingModel;
import com.github.app.dify.knowledgebase.service.EmbeddingService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量化服务（带缓存优化）
 */
@Service
@CacheConfig(cacheNames = "embedding")
public class EmbeddingServiceImpl implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingServiceImpl.class);
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 缓存每个模型的WebClient实例
    private final Map<Long, WebClient> webClientCache = new ConcurrentHashMap<>();
    
    /**
     * 根据 provider 类型构建完整的 API URL
     */
    private String buildApiUrl(EmbeddingModel model) {
        String apiUrl = model.getApiUrl();
        String provider = model.getProviderType() != null ? model.getProviderType() : 
                         (model.getProvider() != null ? model.getProvider() : "openai");
        ProviderType providerType = ProviderType.fromValue(provider);
        
        // 如果 URL 已经包含路径（包含 /api/ 或 /v1/），则直接使用
        if (apiUrl.contains("/api/") || apiUrl.contains("/v1/")) {
            return apiUrl;
        }
        
        // 根据 provider 类型添加相应的路径
        String path = providerType.getEmbeddingPath();
        return apiUrl.endsWith("/") ? apiUrl + path.substring(1) : apiUrl + path;
    }
    
    private WebClient getWebClient(Long modelId) {
        return webClientCache.computeIfAbsent(modelId, id -> {
            EmbeddingModel model = modelConfigService.getEmbeddingModelById(id);
            String baseUrl = buildApiUrl(model);
            String provider = model.getProviderType() != null ? model.getProviderType() : 
                             (model.getProvider() != null ? model.getProvider() : "openai");
            ProviderType providerType = ProviderType.fromValue(provider);
            
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    // 增加缓冲区大小以支持大响应（默认256KB，增加到50MB以支持大批量向量化）
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));
            
            // 根据 provider 类型决定是否需要 API Key
            if (providerType.requiresApiKey() && model.getApiKey() != null && !model.getApiKey().trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + model.getApiKey());
            }
            
            WebClient client = builder.build();
            logger.info("EmbeddingService WebClient已创建 - 模型ID: {}, 名称: {}, Provider: {}, URL: {}, 缓冲区大小: 50MB", 
                    id, model.getName(), providerType.getValue(), baseUrl);
            return client;
        });
    }
    
    /**
     * 向量化单个文本（使用默认模型）
     */
    @Override
    public List<Float> embed(String text) {
        return embed(text, null);
    }
    
    /**
     * 向量化单个文本（使用指定模型，带缓存）
     * 缓存键格式: embedding:query:{modelId}:{text的MD5}
     * 缓存时间: 7天（查询的embedding可以复用）
     * 
     * 优化点：
     * 1. 使用MD5替代hashCode，避免哈希冲突
     * 2. 统一缓存键格式，便于管理和监控
     * 3. 缓存键长度控制在合理范围内
     */
    @Override
    @Cacheable(key = "@cacheKeyGenerator.generateEmbeddingKey(#modelId, #text)")
    public List<Float> embed(String text, Long modelId) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        
        List<String> texts = new ArrayList<>();
        texts.add(text);
        List<List<Float>> embeddings = embedBatch(texts, modelId);
        return embeddings.isEmpty() ? new ArrayList<>() : embeddings.get(0);
    }
    
    /**
     * 批量向量化（使用默认模型）
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return embedBatch(texts, null);
    }
    
    /**
     * 批量向量化（使用指定模型）
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts, Long modelId) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        
        try {
            // 获取模型配置
            EmbeddingModel model = modelConfigService.getEmbeddingModelById(modelId);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", texts);
            if (model.getModel() != null && !model.getModel().trim().isEmpty()) {
                requestBody.put("model", model.getModel());
            }
            
            String apiUrl = buildApiUrl(model);
            String provider = model.getProviderType() != null ? model.getProviderType() : 
                             (model.getProvider() != null ? model.getProvider() : "openai");
            logger.debug("调用向量化API - 模型ID: {}, 名称: {}, Provider: {}, URL: {}, 文本数量: {}", 
                    model.getId(), model.getName(), provider, apiUrl, texts.size());
            
            int timeout = model.getTimeout() != null ? model.getTimeout() : 300000;
            String response = getWebClient(model.getId())
                    .post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
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
                throw new BusinessException("向量化失败", ErrorCode.API_CALL_FAILED);
            }
            
            // 解析响应
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
            
            if (data == null || data.isEmpty()) {
                throw new BusinessException("向量化失败", ErrorCode.API_CALL_FAILED);
            }
            
            List<List<Float>> embeddings = new ArrayList<>();
            for (Map<String, Object> item : data) {
                @SuppressWarnings("unchecked")
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
            
            logger.debug("向量化成功 - 模型ID: {}, 文本数量: {}, 向量维度: {}", 
                    model.getId(), embeddings.size(), 
                    embeddings.isEmpty() ? 0 : embeddings.get(0).size());
            
            return embeddings;
            
        } catch (Exception e) {
            logger.error("向量化失败 - 模型ID: {}", modelId, e);
            throw new BusinessException("向量化失败", ErrorCode.API_CALL_FAILED, e);
        }
    }
    
    /**
     * 分批向量化（处理大量文本，使用默认模型）
     */
    @Override
    public List<List<Float>> embedBatchWithChunking(List<String> texts) {
        return embedBatchWithChunking(texts, null);
    }
    
    /**
     * 分批向量化（处理大量文本，使用指定模型）
     */
    @Override
    public List<List<Float>> embedBatchWithChunking(List<String> texts, Long modelId) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        
        EmbeddingModel model = modelConfigService.getEmbeddingModelById(modelId);
        int batchSize = model.getBatchSize() != null ? model.getBatchSize() : 100;
        List<List<Float>> allEmbeddings = new ArrayList<>();
        
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            try {
                List<List<Float>> batchEmbeddings = embedBatch(batch, modelId);
                allEmbeddings.addAll(batchEmbeddings);
                
                // 避免请求过快
                if (i + batchSize < texts.size()) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                logger.error("批量向量化失败 - 模型ID: {}, 批次: {}-{}", modelId, i, end, e);
                throw new BusinessException("批量向量化失败", ErrorCode.API_CALL_FAILED, e);
            }
        }
        
        return allEmbeddings;
    }
}
