package com.github.app.dify.knowledgebase.service.strategy;

import com.github.app.dify.system.config.QdrantConfig;
import com.github.app.dify.system.config.DocumentReaderConfig;
import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import com.github.app.dify.knowledgebase.util.VectorDatabaseConfigHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.*;
import java.util.Optional;
/**
 * Qdrant向量存储策略实现
 */
@Service
public class QdrantVectorStoreStrategy implements VectorStoreStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(QdrantVectorStoreStrategy.class);
    
    @Override
    public String getType() {
        return "qdrant";
    }
    
    @Autowired
    private QdrantConfig qdrantConfig;
    
    @Autowired(required = false)
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired
    private VectorDatabaseConfigHelper configHelper;
    
    @Autowired(required = false)
    private DocumentReaderConfig documentReaderConfig;
    
    // 为每个知识库缓存WebClient（因为不同知识库可能使用不同的配置）
    private final Map<Long, WebClient> webClientCache = new HashMap<>();
    private final Map<Long, String> lastUrlCache = new HashMap<>();
    private final Map<Long, String> lastApiKeyCache = new HashMap<>();
    
    /**
     * 获取指定知识库的WebClient
     */
    private WebClient getWebClient(Long knowledgeBaseId) {
        // 获取知识库的向量数据库配置
        VectorDatabase vectorDatabaseConfig = getVectorDatabaseConfig(knowledgeBaseId);
        
        String currentUrl;
        String currentApiKey;
        int timeout;
        
        if (vectorDatabaseConfig != null) {
            currentUrl = vectorDatabaseConfig.getUrl();
            currentApiKey = vectorDatabaseConfig.getApiKey();
            timeout = vectorDatabaseConfig.getTimeout() != null ? vectorDatabaseConfig.getTimeout() : 30000;
            logger.debug("使用知识库指定的Qdrant配置 - 知识库ID: {}, 配置ID: {}, URL: {}", 
                    knowledgeBaseId, vectorDatabaseConfig.getId(), currentUrl);
        } else {
            // 使用默认配置
            currentUrl = qdrantConfig.getUrl();
            currentApiKey = qdrantConfig.getApiKey();
            timeout = qdrantConfig.getTimeout();
            logger.debug("使用默认Qdrant配置 - 知识库ID: {}, URL: {}", knowledgeBaseId, currentUrl);
        }
        
        // 检查缓存
        String lastUrl = lastUrlCache.get(knowledgeBaseId);
        String lastApiKey = lastApiKeyCache.get(knowledgeBaseId);
        WebClient webClient = webClientCache.get(knowledgeBaseId);
        
        if (webClient == null || 
            !currentUrl.equals(lastUrl) || 
            (currentApiKey != null ? !currentApiKey.equals(lastApiKey) : lastApiKey != null)) {
            
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(currentUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            if (currentApiKey != null && !currentApiKey.trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + currentApiKey);
            }
            
            webClient = builder.build();
            webClientCache.put(knowledgeBaseId, webClient);
            lastUrlCache.put(knowledgeBaseId, currentUrl);
            lastApiKeyCache.put(knowledgeBaseId, currentApiKey);
            logger.debug("重新创建Qdrant WebClient - 知识库ID: {}, URL: {}", knowledgeBaseId, currentUrl);
        }
        return webClient;
    }
    
    /**
     * 获取知识库的向量数据库配置
     */
    private VectorDatabase getVectorDatabaseConfig(Long knowledgeBaseId) {
        try {
            // 如果是文档解读（knowledgeBaseId为0），从DocumentReaderConfig读取vectorDatabaseId
            if (knowledgeBaseId != null && knowledgeBaseId == 0L && documentReaderConfig != null) {
                Long vectorDatabaseId = documentReaderConfig.getVectorDatabaseId();
                if (vectorDatabaseId != null && vectorDatabaseRepository != null) {
                    Optional<VectorDatabase> config = vectorDatabaseRepository.findById(vectorDatabaseId);
                    if (config.isPresent()) {
                        logger.debug("从文档解读配置读取向量数据库配置 - 配置ID: {}", vectorDatabaseId);
                        return config.get();
                    } else {
                        logger.warn("文档解读配置的向量数据库ID不存在: {}, 使用默认配置", vectorDatabaseId);
                    }
                }
            }
            
            // 从知识库读取vectorDatabaseId
            if (knowledgeBaseRepository != null) {
                Optional<KnowledgeBase> kb = knowledgeBaseRepository.findById(knowledgeBaseId);
                if (kb.isPresent() && kb.get().getVectorDatabaseId() != null) {
                    Long vectorDatabaseId = kb.get().getVectorDatabaseId();
                    if (vectorDatabaseRepository != null) {
                        Optional<VectorDatabase> config = vectorDatabaseRepository.findById(vectorDatabaseId);
                        if (config.isPresent()) {
                            logger.debug("从知识库读取向量数据库配置 - 知识库ID: {}, 配置ID: {}", 
                                    knowledgeBaseId, vectorDatabaseId);
                            return config.get();
                        }
                    }
                }
            }
            
            // 如果没有指定配置，使用默认的Qdrant配置
            VectorDatabase defaultConfig = configHelper.getConfigByType("qdrant");
            if (defaultConfig != null) {
                logger.debug("使用默认Qdrant配置 - 知识库ID: {}", knowledgeBaseId);
                return defaultConfig;
            }
        } catch (Exception e) {
            logger.warn("获取向量数据库配置失败 - 知识库ID: {}", knowledgeBaseId, e);
        }
        
        return null;
    }
    
    /**
     * 获取或创建知识库对应的集合
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        WebClient webClient = getWebClient(knowledgeBaseId);
        VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
        int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
        
        try {
            // 检查集合是否存在
            String checkUrl = "/collections/" + collectionName;
            try {
                Map<String, Object> response = webClient
                        .get()
                        .uri(checkUrl)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .timeout(Duration.ofMillis(timeout))
                        .block();
                
                if (response != null && response.containsKey("result")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) response.get("result");
                    // 检查集合配置是否匹配
                if (result.containsKey("config")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> collectionConfig = (Map<String, Object>) result.get("config");
                    if (collectionConfig.containsKey("params")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) collectionConfig.get("params");
                            if (params.containsKey("vectors")) {
                                Object vectorsObj = params.get("vectors");
                                // 检查向量配置格式
                                // 单向量格式：vectors直接包含size和distance
                                // 命名向量格式：vectors是一个Map，键是向量名称，值是配置
                                if (vectorsObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> vectors = (Map<String, Object>) vectorsObj;
                                    
                                    // 检查是否是单向量格式（直接包含size键）
                                    if (vectors.containsKey("size")) {
                                        Object sizeObj = vectors.get("size");
                                        if (sizeObj instanceof Number && ((Number) sizeObj).intValue() == vectorSize) {
                                            logger.debug("Qdrant集合已存在且配置匹配（单向量格式）- 知识库ID: {}, 集合名: {}", 
                                                    knowledgeBaseId, collectionName);
                                            return;
                                        } else {
                                            logger.warn("集合配置不匹配，将删除并重新创建 - 知识库ID: {}, 集合名: {}, 期望维度: {}, 实际维度: {}", 
                                                    knowledgeBaseId, collectionName, vectorSize, sizeObj);
                                            // 删除旧集合
                                            deleteCollection(collectionName, knowledgeBaseId);
                                        }
                                    } else {
                                        // 命名向量格式（vectors的键是向量名称）
                                        logger.warn("集合使用命名向量格式，将删除并重新创建为单向量格式 - 知识库ID: {}, 集合名: {}", 
                                                knowledgeBaseId, collectionName);
                                        // 删除旧集合
                                        deleteCollection(collectionName, knowledgeBaseId);
                                    }
                                } else {
                                    logger.warn("集合向量配置格式未知，将删除并重新创建 - 知识库ID: {}, 集合名: {}", 
                                            knowledgeBaseId, collectionName);
                                    deleteCollection(collectionName, knowledgeBaseId);
                                }
                            }
                        }
                    } else {
                        logger.debug("Qdrant集合已存在 - 知识库ID: {}, 集合名: {}", knowledgeBaseId, collectionName);
                        return;
                    }
                }
            } catch (Exception e) {
                // 集合不存在，继续创建
                logger.debug("集合不存在，将创建 - 知识库ID: {}, 集合名: {}", knowledgeBaseId, collectionName);
            }
            
            // 创建集合（使用单向量格式）
            // Qdrant单向量格式：vectors直接包含size和distance，不使用params包装
            Map<String, Object> createRequest = new HashMap<>();
            Map<String, Object> vectorsConfig = new HashMap<>();
            vectorsConfig.put("size", vectorSize);
            vectorsConfig.put("distance", "Cosine");
            createRequest.put("vectors", vectorsConfig);
            
            logger.debug("创建集合请求 - 知识库ID: {}, 集合名: {}, 请求体: {}", 
                    knowledgeBaseId, collectionName, createRequest);
            
            webClient
                    .put()
                    .uri(checkUrl)
                    .bodyValue(createRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            logger.info("创建Qdrant集合成功 - 知识库ID: {}, 集合名: {}, 向量维度: {}", 
                    knowledgeBaseId, collectionName, vectorSize);
            
        } catch (Exception e) {
            logger.error("确保Qdrant集合存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("确保Qdrant集合存在失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量插入向量
     */
    @Override
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                               List<List<Float>> vectors, List<String> texts, 
                               List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            logger.warn("向量列表为空，跳过插入 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        String collectionName = getCollectionName(knowledgeBaseId);
        int vectorSize = vectors.get(0).size();
        
        // 确保集合存在（如果格式不对会自动删除并重新创建）
        ensureCollection(knowledgeBaseId, vectorSize);
        
        try {
            List<Map<String, Object>> points = new ArrayList<>();
            
            // 检查集合配置，确定是否使用命名向量
            boolean useNamedVectors = checkIfCollectionUsesNamedVectors(collectionName, knowledgeBaseId);
            logger.debug("集合向量格式检查 - 知识库ID: {}, 集合名: {}, 使用命名向量: {}", 
                    knowledgeBaseId, collectionName, useNamedVectors);
            
            // 如果检测到使用命名向量，但我们的代码期望单向量格式，强制删除并重新创建
            if (useNamedVectors) {
                logger.warn("检测到集合使用命名向量格式，但代码期望单向量格式，将删除并重新创建 - 知识库ID: {}, 集合名: {}", 
                        knowledgeBaseId, collectionName);
                deleteCollection(collectionName, knowledgeBaseId);
                ensureCollection(knowledgeBaseId, vectorSize);
                useNamedVectors = false; // 重新创建后使用单向量格式
            }
            
            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                
                // 生成唯一ID
                long pointId = generatePointId(knowledgeBaseId, documentId, chunkIndex);
                
                Map<String, Object> point = new HashMap<>();
                point.put("id", pointId);
                
                // 使用单向量格式：直接使用vector字段（我们总是使用单向量格式）
                point.put("vector", vector);
                
                Map<String, Object> payload = new HashMap<>();
                payload.put("document_id", documentId);
                payload.put("chunk_index", chunkIndex);
                payload.put("text", text);
                payload.put("knowledge_base_id", knowledgeBaseId);
                point.put("payload", payload);
                
                points.add(point);
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("points", points);
            requestBody.put("wait", true); // 等待操作完成
            
            logger.debug("发送向量插入请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 向量数量: {}, 第一个向量维度: {}", 
                    knowledgeBaseId, documentId, collectionName, points.size(), 
                    !vectors.isEmpty() ? vectors.get(0).size() : 0);
            
            // 记录插入前的点数
            long pointsCountBefore = getCollectionPointsCount(collectionName, knowledgeBaseId);
            logger.debug("插入前集合点数: {} - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    pointsCountBefore, knowledgeBaseId, documentId, collectionName);
            
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            Map<String, Object> response = webClient
                    .put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collections/" + collectionName + "/points")
                            .queryParam("wait", "true") // 在URL参数中也添加wait
                            .build())
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "向量插入请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("向量插入请求失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, documentId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            logger.info("向量插入请求完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {}, 响应: {}", 
                    knowledgeBaseId, documentId, points.size(), response);
            
            // 检查响应状态
            if (response != null) {
                Object statusObj = response.get("status");
                Object resultObj = response.get("result");
                if (resultObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) resultObj;
                    Object operationStatus = result.get("status");
                    logger.debug("操作状态 - 知识库ID: {}, 文档ID: {}, status: {}, operation_status: {}", 
                            knowledgeBaseId, documentId, statusObj, operationStatus);
                }
            }
            
            // 验证插入是否成功：等待一小段时间后检查集合点数（即使使用了wait=true，也可能需要短暂等待索引更新）
            try {
                Thread.sleep(200); // 等待200ms让Qdrant更新索引
                long pointsCountAfter = getCollectionPointsCount(collectionName, knowledgeBaseId);
                long expectedCount = pointsCountBefore + points.size();
                logger.info("验证向量插入 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 插入前点数: {}, 插入后点数: {}, 期望点数: {}", 
                        knowledgeBaseId, documentId, collectionName, pointsCountBefore, pointsCountAfter, expectedCount);
                
                if (pointsCountAfter < expectedCount) {
                    logger.warn("向量插入后点数不符合预期 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 实际: {}, 期望: {}", 
                            knowledgeBaseId, documentId, collectionName, pointsCountAfter, expectedCount);
                    // 再等待一段时间后重试检查
                    Thread.sleep(1000);
                    pointsCountAfter = getCollectionPointsCount(collectionName, knowledgeBaseId);
                    logger.info("重试验证向量插入 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 插入后点数: {}", 
                            knowledgeBaseId, documentId, collectionName, pointsCountAfter);
                }
            } catch (Exception e) {
                logger.warn("验证向量插入时出错 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            }
            
        } catch (Exception e) {
            logger.error("向量插入失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("向量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 向量检索
     */
    @Override
    public List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 先检查集合是否存在
        if (!collectionExists(collectionName, knowledgeBaseId)) {
            logger.warn("Qdrant集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                    knowledgeBaseId, collectionName);
            return new ArrayList<>();
        }
        
        // 检查集合是否为空（没有向量点）
        long pointsCount = getCollectionPointsCount(collectionName, knowledgeBaseId);
        if (pointsCount == 0) {
            logger.warn("Qdrant集合为空 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                    knowledgeBaseId, collectionName);
            return new ArrayList<>();
        }
        
        // 检查查询向量是否为空或维度为0
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", queryVector);
            requestBody.put("limit", topK);
            requestBody.put("with_payload", true);
            
            logger.debug("发送Qdrant搜索请求 - 知识库ID: {}, 集合名: {}, 向量维度: {}, topK: {}, 请求体: {}", 
                    knowledgeBaseId, collectionName, queryVector.size(), topK, requestBody);
            
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            Map<String, Object> response = webClient
                    .post()
                    .uri("/collections/" + collectionName + "/points/search")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            clientResponse -> {
                                // 读取响应体以获取详细错误信息
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "Qdrant搜索请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("Qdrant搜索请求失败 - 知识库ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();
            
            if (response != null && response.containsKey("result")) {
                Object resultObj = response.get("result");
                if (resultObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> resultList = (List<Map<String, Object>>) resultObj;
                    
                    for (Map<String, Object> item : resultList) {
                        VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();
                        
                        Object scoreObj = item.get("score");
                        if (scoreObj instanceof Number) {
                            result.setScore(((Number) scoreObj).doubleValue());
                        }
                        
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = (Map<String, Object>) item.get("payload");
                        if (payload != null) {
                            Object textObj = payload.get("text");
                            if (textObj != null) {
                                result.setText(textObj.toString());
                            }
                            
                            Object docIdObj = payload.get("document_id");
                            if (docIdObj instanceof Number) {
                                result.setDocumentId(((Number) docIdObj).longValue());
                            }
                            
                            Object chunkIndexObj = payload.get("chunk_index");
                            if (chunkIndexObj instanceof Number) {
                                result.setChunkIndex(((Number) chunkIndexObj).intValue());
                            }
                        }
                        
                        results.add(result);
                    }
                }
            }
            
            logger.debug("向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}", 
                    knowledgeBaseId, topK, results.size());
            
            return results;
            
        } catch (WebClientResponseException e) {
            logger.error("向量检索失败 - 知识库ID: {}, 集合名: {}, HTTP状态: {}, 响应体: {}", 
                    knowledgeBaseId, collectionName, e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 如果是404，说明集合不存在，返回空结果
            if (e.getStatusCode().value() == 404) {
                logger.warn("Qdrant集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                        knowledgeBaseId, collectionName);
                return new ArrayList<>();
            }
            
            throw new RuntimeException("向量检索失败: " + e.getMessage() + 
                    (e.getResponseBodyAsString() != null ? " - " + e.getResponseBodyAsString() : ""), e);
        } catch (Exception e) {
            logger.error("向量检索失败 - 知识库ID: {}, 集合名: {}", knowledgeBaseId, collectionName, e);
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取集合的点数
     */
    private long getCollectionPointsCount(String collectionName) {
        return getCollectionPointsCount(collectionName, 0L);
    }
    
    private long getCollectionPointsCount(String collectionName, Long knowledgeBaseId) {
        try {
            String checkUrl = "/collections/" + collectionName;
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            Map<String, Object> response = webClient
                    .get()
                    .uri(checkUrl)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                if (clientResponse.statusCode().value() == 404) {
                                    return Mono.empty();
                                }
                                return Mono.error(new RuntimeException(
                                        "获取集合信息失败: HTTP " + clientResponse.statusCode()));
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            if (response != null && response.containsKey("result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result.containsKey("points_count")) {
                    Object pointsCountObj = result.get("points_count");
                    if (pointsCountObj instanceof Number) {
                        return ((Number) pointsCountObj).longValue();
                    }
                }
            }
            return 0;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return 0;
            }
            logger.warn("获取集合点数失败 - 集合名: {}, HTTP状态: {}", collectionName, e.getStatusCode());
            return 0;
        } catch (Exception e) {
            logger.warn("获取集合点数失败 - 集合名: {}", collectionName, e);
            return 0;
        }
    }
    
    /**
     * 检查集合是否存在
     */
    private boolean collectionExists(String collectionName) {
        return collectionExists(collectionName, 0L);
    }
    
    private boolean collectionExists(String collectionName, Long knowledgeBaseId) {
        try {
            String checkUrl = "/collections/" + collectionName;
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            Map<String, Object> response = webClient
                    .get()
                    .uri(checkUrl)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                // 404表示集合不存在，这是正常情况
                                if (clientResponse.statusCode().value() == 404) {
                                    return Mono.empty();
                                }
                                return Mono.error(new RuntimeException(
                                        "检查集合存在性失败: HTTP " + clientResponse.statusCode()));
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            return response != null;
        } catch (WebClientResponseException e) {
            // 404表示集合不存在，这是正常情况
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            logger.warn("检查集合存在性失败 - 集合名: {}, HTTP状态: {}", collectionName, e.getStatusCode());
            return false;
        } catch (Exception e) {
            // 其他异常，记录日志但返回false
            logger.warn("检查集合存在性失败 - 集合名: {}", collectionName, e);
            return false;
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 先检查集合是否存在，如果不存在则无需删除
        if (!collectionExists(collectionName, knowledgeBaseId)) {
            logger.info("集合不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            return;
        }
        
        // 检查集合是否为空，如果为空则无需删除
        long pointsCount = getCollectionPointsCount(collectionName, knowledgeBaseId);
        if (pointsCount == 0) {
            logger.info("集合为空，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            return;
        }
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> filter = new HashMap<>();
            List<Map<String, Object>> must = new ArrayList<>();
            
            // Qdrant API filter格式：直接使用key和match，不需要field包装
            Map<String, Object> condition = new HashMap<>();
            condition.put("key", "document_id");
            Map<String, Object> match = new HashMap<>();
            match.put("value", documentId);
            condition.put("match", match);
            
            must.add(condition);
            filter.put("must", must);
            requestBody.put("filter", filter);
            
            logger.debug("发送删除向量请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 请求体: {}", 
                    knowledgeBaseId, documentId, collectionName, requestBody);
            
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            webClient
                    .post()
                    .uri("/collections/" + collectionName + "/points/delete")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                // 读取响应体以获取详细错误信息
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "删除向量请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("删除向量请求失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, documentId, collectionName, clientResponse.statusCode(), errorBody);
                                    
                                    // 如果是400错误且集合为空，可能是没有匹配的点，这是正常情况
                                    if (clientResponse.statusCode().value() == 400 && pointsCount == 0) {
                                        logger.warn("集合为空，删除操作可能失败，但这是正常情况 - 知识库ID: {}, 文档ID: {}", 
                                                knowledgeBaseId, documentId);
                                        return Mono.empty(); // 返回空，不抛出异常
                                    }
                                    
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
        } catch (WebClientResponseException e) {
            // 如果是400错误，可能是没有匹配的点，记录警告但不抛出异常
            if (e.getStatusCode().value() == 400) {
                logger.warn("删除向量返回400错误（可能是没有匹配的点）- 知识库ID: {}, 文档ID: {}, 集合名: {}, 响应: {}", 
                        knowledgeBaseId, documentId, collectionName, e.getResponseBodyAsString());
                // 不抛出异常，因为可能是正常的（没有向量需要删除）
                return;
            }
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}, HTTP状态: {}, 响应: {}", 
                    knowledgeBaseId, documentId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("删除文档向量失败: " + e.getMessage() + 
                    (e.getResponseBodyAsString() != null ? " - " + e.getResponseBodyAsString() : ""), e);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("删除文档向量失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除集合
     */
    private void deleteCollection(String collectionName) {
        deleteCollection(collectionName, 0L);
    }
    
    private void deleteCollection(String collectionName, Long knowledgeBaseId) {
        try {
            String deleteUrl = "/collections/" + collectionName;
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = config != null && config.getTimeout() != null ? config.getTimeout() : qdrantConfig.getTimeout();
            
            webClient
                    .delete()
                    .uri(deleteUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            logger.info("删除Qdrant集合成功 - 集合名: {}", collectionName);
        } catch (Exception e) {
            logger.warn("删除Qdrant集合失败 - 集合名: {}", collectionName, e);
            // 不抛出异常，继续尝试创建
        }
    }
    
    /**
     * 检查集合是否使用命名向量
     */
    private boolean checkIfCollectionUsesNamedVectors(String collectionName) {
        return checkIfCollectionUsesNamedVectors(collectionName, 0L);
    }
    
    private boolean checkIfCollectionUsesNamedVectors(String collectionName, Long knowledgeBaseId) {
        try {
            String checkUrl = "/collections/" + collectionName;
            WebClient webClient = getWebClient(knowledgeBaseId);
            VectorDatabase vectorDbConfig = getVectorDatabaseConfig(knowledgeBaseId);
            int timeout = vectorDbConfig != null && vectorDbConfig.getTimeout() != null ? vectorDbConfig.getTimeout() : qdrantConfig.getTimeout();
            
            Map<String, Object> response = webClient
                    .get()
                    .uri(checkUrl)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(timeout))
                    .block();
            
            if (response != null && response.containsKey("result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                if (result.containsKey("config")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> collectionConfig = (Map<String, Object>) result.get("config");
                    if (collectionConfig.containsKey("params")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) collectionConfig.get("params");
                        if (params.containsKey("vectors")) {
                            Object vectorsObj = params.get("vectors");
                            // 检查向量配置格式
                            if (vectorsObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> vectors = (Map<String, Object>) vectorsObj;
                                // 如果直接包含size键，说明是单向量格式
                                if (vectors.containsKey("size")) {
                                    return false; // 单向量格式
                                } else {
                                    // 否则是命名向量格式（键是向量名称）
                                    return !vectors.isEmpty();
                                }
                            }
                        }
                    }
                }
            }
            // 默认使用单向量格式
            return false;
        } catch (Exception e) {
            logger.warn("检查集合向量格式失败 - 集合名: {}, 使用默认单向量格式", collectionName, e);
            return false; // 默认使用单向量格式
        }
    }
    
    /**
     * 获取集合名称
     */
    private String getCollectionName(Long knowledgeBaseId) {
        return "kb_" + knowledgeBaseId;
    }
    
    /**
     * 生成点ID
     */
    private long generatePointId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        // 使用简单的哈希组合，确保唯一性
        return (knowledgeBaseId * 1000000000L + documentId * 10000L + chunkIndex);
    }
    
}