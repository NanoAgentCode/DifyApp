package com.github.app.dify.service;

import com.github.app.dify.config.MilvusConfig;
import com.github.app.dify.domain.VectorDatabase;
import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.repository.VectorDatabaseRepository;
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

/**
 * Milvus向量存储服务（使用HTTP REST API）
 * 支持Milvus和Milvus Lite
 */
@Service
public class MilvusVectorStoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStoreService.class);
    
    @Autowired
    private MilvusConfig milvusConfig;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    // 为每个知识库缓存WebClient（因为不同知识库可能使用不同的配置）
    private final Map<Long, WebClient> webClientCache = new HashMap<>();
    private final Map<Long, String> lastUrlCache = new HashMap<>();
    private final Map<Long, String> lastApiKeyCache = new HashMap<>();
    
    /**
     * 获取指定知识库的WebClient
     */
    private WebClient getWebClient(Long knowledgeBaseId) {
        // 获取知识库的向量存储类型
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        
        // 获取对应的配置（Milvus 和 Milvus Lite 兼容，使用相同的配置）
        String currentUrl;
        String currentApiKey;
        
        // Milvus Lite 和 Milvus 完全兼容，优先查找对应类型的配置，如果没有则使用 Milvus 配置
        if ("milvus-lite".equalsIgnoreCase(vectorStoreType)) {
            // Milvus Lite：先尝试从数据库获取 milvus-lite 配置
            VectorDatabase config = getConfigByType("milvus-lite");
            if (config != null) {
                currentUrl = config.getUrl();
                currentApiKey = config.getApiKey();
            } else {
                // 如果没有 milvus-lite 配置，尝试使用 milvus 配置（两者兼容）
                config = getConfigByType("milvus");
                if (config != null) {
                    currentUrl = config.getUrl();
                    currentApiKey = config.getApiKey();
                } else {
                    // 如果都没有，使用默认的 MilvusConfig
                    currentUrl = milvusConfig.getUrl();
                    currentApiKey = milvusConfig.getApiKey();
                }
            }
        } else {
            // Milvus：使用 MilvusConfig 或数据库中的 milvus 配置
            VectorDatabase config = getConfigByType("milvus");
            if (config != null) {
                currentUrl = config.getUrl();
                currentApiKey = config.getApiKey();
            } else {
                currentUrl = milvusConfig.getUrl();
                currentApiKey = milvusConfig.getApiKey();
            }
        }
        
        // 检查URL是否有效
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            logger.warn("Milvus URL为空，使用默认URL: http://localhost:19530");
            currentUrl = "http://localhost:19530";
        } else if (!currentUrl.startsWith("http://") && !currentUrl.startsWith("https://")) {
            // 如果不是HTTP URL，使用默认URL（Milvus 和 Milvus Lite 都需要 HTTP API）
            logger.warn("Milvus URL配置为文件路径: {}，但需要HTTP URL。使用默认URL: http://localhost:19530", currentUrl);
            currentUrl = "http://localhost:19530";
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
            logger.debug("为知识库创建Milvus WebClient - 知识库ID: {}, 类型: {}, URL: {}", 
                    knowledgeBaseId, vectorStoreType, currentUrl);
        }
        return webClient;
    }
    
    /**
     * 获取知识库的向量存储类型
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        if (knowledgeBaseRepository == null) {
            return "milvus"; // 默认
        }
        try {
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .map(kb -> {
                        String type = kb.getVectorStoreType();
                        if (type != null && ("milvus".equalsIgnoreCase(type) || "milvus-lite".equalsIgnoreCase(type))) {
                            return type;
                        }
                        return "milvus"; // 默认
                    })
                    .orElse("milvus");
        } catch (Exception e) {
            logger.warn("获取知识库向量存储类型失败，使用默认值milvus - 知识库ID: {}", knowledgeBaseId, e);
            return "milvus";
        }
    }
    
    /**
     * 根据类型获取配置
     */
    private VectorDatabase getConfigByType(String type) {
        if (vectorDatabaseRepository == null) {
            return null;
        }
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType(type);
            if (defaultConfig.isPresent()) {
                return defaultConfig.get();
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
            if (!enabledConfigs.isEmpty()) {
                return enabledConfigs.get(0);
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType(type);
            if (!allConfigs.isEmpty()) {
                return allConfigs.get(0);
            }
        } catch (Exception e) {
            logger.warn("获取{}配置失败: {}", type, e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取指定知识库的超时时间
     */
    private int getTimeout(Long knowledgeBaseId) {
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        // Milvus 和 Milvus Lite 兼容，优先查找对应类型的配置
        VectorDatabase config = null;
        if ("milvus-lite".equalsIgnoreCase(vectorStoreType)) {
            config = getConfigByType("milvus-lite");
            if (config == null) {
                // 如果没有 milvus-lite 配置，尝试使用 milvus 配置
                config = getConfigByType("milvus");
            }
        } else {
            config = getConfigByType("milvus");
        }
        
        if (config != null && config.getTimeout() != null) {
            return config.getTimeout();
        }
        return milvusConfig.getTimeout();
    }
    
    /**
     * 确保集合存在
     */
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 检查集合是否存在
            if (collectionExists(knowledgeBaseId, collectionName)) {
                // 集合存在，检查配置是否匹配（简化处理，只检查是否存在）
                logger.debug("Milvus集合已存在 - 知识库ID: {}, 集合名: {}", 
                        knowledgeBaseId, collectionName);
                return;
            }
            
            // 创建集合
            createCollection(knowledgeBaseId, collectionName, vectorSize);
            
        } catch (Exception e) {
            logger.error("确保Milvus集合存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("确保Milvus集合存在失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建集合
     */
    private void createCollection(Long knowledgeBaseId, String collectionName, int vectorSize) {
        try {
            // Milvus HTTP API创建集合
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("collection_name", collectionName);
            
            // 定义字段
            List<Map<String, Object>> fields = new ArrayList<>();
            
            // ID字段
            Map<String, Object> idField = new HashMap<>();
            idField.put("field_name", "id");
            idField.put("data_type", "Int64");
            idField.put("is_primary", true);
            idField.put("auto_id", false);
            fields.add(idField);
            
            // 向量字段
            Map<String, Object> vectorField = new HashMap<>();
            vectorField.put("field_name", "vector");
            vectorField.put("data_type", "FloatVector");
            Map<String, Object> typeParams = new HashMap<>();
            typeParams.put("dim", vectorSize);
            vectorField.put("type_params", typeParams);
            fields.add(vectorField);
            
            // document_id字段
            Map<String, Object> docIdField = new HashMap<>();
            docIdField.put("field_name", "document_id");
            docIdField.put("data_type", "Int64");
            fields.add(docIdField);
            
            // chunk_index字段
            Map<String, Object> chunkIndexField = new HashMap<>();
            chunkIndexField.put("field_name", "chunk_index");
            chunkIndexField.put("data_type", "Int32");
            fields.add(chunkIndexField);
            
            // text字段
            Map<String, Object> textField = new HashMap<>();
            textField.put("field_name", "text");
            textField.put("data_type", "VarChar");
            Map<String, Object> textTypeParams = new HashMap<>();
            textTypeParams.put("max_length", 65535);
            textField.put("type_params", textTypeParams);
            fields.add(textField);
            
            // knowledge_base_id字段
            Map<String, Object> kbIdField = new HashMap<>();
            kbIdField.put("field_name", "knowledge_base_id");
            kbIdField.put("data_type", "Int64");
            fields.add(kbIdField);
            
            createRequest.put("fields", fields);
            
            logger.debug("创建Milvus集合请求 - 集合名: {}, 请求体: {}", collectionName, createRequest);
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/collection")
                    .bodyValue(createRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "创建Milvus集合失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("创建Milvus集合失败 - 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            logger.info("创建Milvus集合成功 - 集合名: {}, 向量维度: {}", collectionName, vectorSize);
            
            // 创建索引
            createIndex(knowledgeBaseId, collectionName);
            
            // 加载集合
            loadCollection(knowledgeBaseId, collectionName);
            
        } catch (Exception e) {
            logger.error("创建Milvus集合失败 - 集合名: {}", collectionName, e);
            throw new RuntimeException("创建Milvus集合失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建索引
     */
    private void createIndex(Long knowledgeBaseId, String collectionName) {
        try {
            Map<String, Object> indexRequest = new HashMap<>();
            indexRequest.put("collection_name", collectionName);
            indexRequest.put("field_name", "vector");
            Map<String, Object> indexParams = new HashMap<>();
            indexParams.put("index_type", "IVF_FLAT");
            indexParams.put("metric_type", "COSINE");
            Map<String, Object> params = new HashMap<>();
            params.put("nlist", 1024);
            indexParams.put("params", params);
            indexRequest.put("index_params", indexParams);
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/index")
                    .bodyValue(indexRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                logger.warn("创建Milvus索引失败，但继续执行 - 集合名: {}, HTTP状态: {}", 
                                        collectionName, clientResponse.statusCode());
                                return Mono.empty();
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            logger.info("创建Milvus索引成功 - 集合名: {}", collectionName);
        } catch (Exception e) {
            logger.warn("创建Milvus索引失败，但继续执行 - 集合名: {}", collectionName, e);
        }
    }
    
    /**
     * 加载集合
     */
    private void loadCollection(Long knowledgeBaseId, String collectionName) {
        try {
            Map<String, Object> loadRequest = new HashMap<>();
            loadRequest.put("collection_name", collectionName);
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/collection/load")
                    .bodyValue(loadRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                logger.warn("加载Milvus集合失败，但继续执行 - 集合名: {}, HTTP状态: {}", 
                                        collectionName, clientResponse.statusCode());
                                return Mono.empty();
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            logger.debug("加载Milvus集合成功 - 集合名: {}", collectionName);
        } catch (Exception e) {
            logger.warn("加载Milvus集合失败，但继续执行 - 集合名: {}", collectionName, e);
        }
    }
    
    /**
     * 检查集合是否存在
     */
    private boolean collectionExists(Long knowledgeBaseId, String collectionName) {
        try {
            Map<String, Object> response = getWebClient(knowledgeBaseId)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/collection")
                            .queryParam("collection_name", collectionName)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                if (clientResponse.statusCode().value() == 404) {
                                    return Mono.empty();
                                }
                                return Mono.error(new RuntimeException(
                                        "检查集合存在性失败: HTTP " + clientResponse.statusCode()));
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            return response != null;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            logger.warn("检查集合存在性失败 - 集合名: {}, HTTP状态: {}", collectionName, e.getStatusCode());
            return false;
        } catch (Exception e) {
            logger.warn("检查集合存在性失败 - 集合名: {}", collectionName, e);
            return false;
        }
    }
    
    /**
     * 批量插入/更新向量
     */
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                              List<List<Float>> vectors, List<String> texts, 
                              List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            logger.warn("向量列表为空，跳过插入 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 先删除该文档的旧向量
            deleteDocumentVectors(knowledgeBaseId, documentId);
            
            // 准备插入数据
            List<Long> ids = new ArrayList<>();
            List<List<Float>> vectorList = new ArrayList<>();
            List<Long> documentIds = new ArrayList<>();
            List<Integer> chunkIndexList = new ArrayList<>();
            List<String> textList = new ArrayList<>();
            List<Long> knowledgeBaseIdList = new ArrayList<>();
            
            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                
                // 生成唯一ID
                long id = generateId(knowledgeBaseId, documentId, chunkIndex);
                ids.add(id);
                vectorList.add(vector);
                documentIds.add(documentId);
                chunkIndexList.add(chunkIndex);
                textList.add(text);
                knowledgeBaseIdList.add(knowledgeBaseId);
            }
            
            // 构建插入请求
            Map<String, Object> insertRequest = new HashMap<>();
            insertRequest.put("collection_name", collectionName);
            Map<String, Object> data = new HashMap<>();
            data.put("id", ids);
            data.put("vector", vectorList);
            data.put("document_id", documentIds);
            data.put("chunk_index", chunkIndexList);
            data.put("text", textList);
            data.put("knowledge_base_id", knowledgeBaseIdList);
            insertRequest.put("data", data);
            
            logger.debug("发送Milvus向量插入请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, collectionName, vectors.size());
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/entities")
                    .bodyValue(insertRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "插入Milvus向量失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("插入Milvus向量失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, documentId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            logger.info("Milvus向量插入完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, vectors.size());
            
        } catch (Exception e) {
            logger.error("向量插入失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("向量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 向量检索
     */
    public List<SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 检查集合是否存在
        if (!collectionExists(knowledgeBaseId, collectionName)) {
            logger.warn("Milvus集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                    knowledgeBaseId, collectionName);
            return new ArrayList<>();
        }
        
        // 检查查询向量是否为空
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }
        
        try {
            // 确保集合已加载
            loadCollection(knowledgeBaseId, collectionName);
            
            // 构建搜索请求
            Map<String, Object> searchRequest = new HashMap<>();
            searchRequest.put("collection_name", collectionName);
            searchRequest.put("vector", queryVector);
            searchRequest.put("top_k", topK);
            searchRequest.put("metric_type", "COSINE");
            searchRequest.put("output_fields", Arrays.asList("text", "document_id", "chunk_index"));
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("nprobe", 10);
            searchRequest.put("params", searchParams);
            
            logger.debug("发送Milvus搜索请求 - 知识库ID: {}, 集合名: {}, 向量维度: {}, topK: {}", 
                    knowledgeBaseId, collectionName, queryVector.size(), topK);
            
            Map<String, Object> response = getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/search")
                    .bodyValue(searchRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "Milvus搜索失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("Milvus搜索失败 - 知识库ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            List<SearchResult> results = new ArrayList<>();
            
            if (response != null && response.containsKey("results")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) response.get("results");
                
                for (Map<String, Object> item : resultList) {
                    SearchResult result = new SearchResult();
                    
                    // 获取相似度分数
                    Object scoreObj = item.get("distance");
                    if (scoreObj instanceof Number) {
                        // COSINE距离转换为相似度：similarity = 1 - distance
                        double distance = ((Number) scoreObj).doubleValue();
                        double score = 1.0 - distance;
                        result.setScore(score);
                    }
                    
                    // 获取字段值
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entity = (Map<String, Object>) item.get("entity");
                    if (entity != null) {
                        Object textObj = entity.get("text");
                        if (textObj != null) {
                            result.setText(textObj.toString());
                        }
                        
                        Object docIdObj = entity.get("document_id");
                        if (docIdObj instanceof Number) {
                            result.setDocumentId(((Number) docIdObj).longValue());
                        }
                        
                        Object chunkIndexObj = entity.get("chunk_index");
                        if (chunkIndexObj instanceof Number) {
                            result.setChunkIndex(((Number) chunkIndexObj).intValue());
                        }
                    }
                    
                    results.add(result);
                }
            }
            
            logger.debug("向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}", 
                    knowledgeBaseId, topK, results.size());
            
            return results;
            
        } catch (WebClientResponseException e) {
            logger.error("向量检索失败 - 知识库ID: {}, 集合名: {}, HTTP状态: {}, 响应体: {}", 
                    knowledgeBaseId, collectionName, e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            if (e.getStatusCode().value() == 404) {
                logger.warn("Milvus集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
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
     * 删除文档的所有向量
     */
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 检查集合是否存在
        if (!collectionExists(knowledgeBaseId, collectionName)) {
            logger.info("集合不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            return;
        }
        
        try {
            // 构建删除请求
            Map<String, Object> deleteRequest = new HashMap<>();
            deleteRequest.put("collection_name", collectionName);
            deleteRequest.put("expr", String.format("document_id == %d", documentId));
            
            logger.debug("发送删除Milvus向量请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/api/v1/entities/delete")
                    .bodyValue(deleteRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "删除Milvus向量失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.warn("删除Milvus向量失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, documentId, collectionName, clientResponse.statusCode(), errorBody);
                                    
                                    // 如果是400错误，可能是没有匹配的点，这是正常情况
                                    if (clientResponse.statusCode().value() == 400) {
                                        return Mono.empty(); // 返回空，不抛出异常
                                    }
                                    
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 400) {
                logger.warn("删除向量返回400错误（可能是没有匹配的点）- 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                        knowledgeBaseId, documentId, collectionName);
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
     * 获取集合名称
     */
    private String getCollectionName(Long knowledgeBaseId) {
        return "kb_" + knowledgeBaseId;
    }
    
    /**
     * 生成ID
     */
    private long generateId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        // 使用简单的哈希组合，确保唯一性
        return (knowledgeBaseId * 1000000000L + documentId * 10000L + chunkIndex);
    }
    
    /**
     * 检索结果
     */
    public static class SearchResult {
        private double score;
        private String text;
        private Long documentId;
        private Integer chunkIndex;
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public Long getDocumentId() {
            return documentId;
        }
        
        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
        
        public Integer getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }
}
