package com.github.app.dify.appknowledgebase.service.impl;

import com.github.app.dify.appsystemdata.config.WeaviateConfig;
import com.github.app.dify.appknowledgebase.service.VectorStoreStrategy;
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
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
/**
 * Weaviate向量存储服务（使用HTTP REST API）
 */
@Service
public class WeaviateVectorStoreStrategy implements VectorStoreStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(WeaviateVectorStoreStrategy.class);
    
    @Override
    public String getType() {
        return "weaviate";
    }
    
    @Autowired
    private WeaviateConfig weaviateConfig;
    
    @Autowired(required = false)
    private com.github.app.dify.appknowledgebase.repository.VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private com.github.app.dify.appknowledgebase.repository.KnowledgeBaseRepository knowledgeBaseRepository;
    
    // 为每个知识库缓存WebClient（因为不同知识库可能使用不同的配置）
    private final Map<Long, WebClient> clientCache = new HashMap<>();
    private final Map<Long, String> lastUrlCache = new HashMap<>();
    private final Map<Long, String> lastApiKeyCache = new HashMap<>();
    
    /**
     * 获取指定知识库的WebClient
     */
    private WebClient getWebClient(Long knowledgeBaseId) {
        // 获取知识库的向量存储类型
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        
        // 获取对应的配置
        String currentUrl;
        String currentApiKey;
        
        // 使用 WeaviateConfig 或数据库中的 weaviate 配置
        com.github.app.dify.appknowledgebase.domain.VectorDatabase config = getConfigByType("weaviate");
        if (config != null) {
            currentUrl = config.getUrl();
            currentApiKey = config.getApiKey();
        } else {
            currentUrl = weaviateConfig.getUrl();
            currentApiKey = weaviateConfig.getApiKey();
        }
        
        // 检查缓存
        String lastUrl = lastUrlCache.get(knowledgeBaseId);
        String lastApiKey = lastApiKeyCache.get(knowledgeBaseId);
        WebClient client = clientCache.get(knowledgeBaseId);
        
        if (client == null || 
            !currentUrl.equals(lastUrl) || 
            (currentApiKey != null ? !currentApiKey.equals(lastApiKey) : lastApiKey != null)) {
            
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(currentUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    // 增加缓冲区大小以支持大批量向量插入响应（默认256KB，增加到50MB）
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024));
            
            if (currentApiKey != null && !currentApiKey.trim().isEmpty()) {
                // Weaviate使用X-API-Key头
                builder.defaultHeader("X-API-Key", currentApiKey);
            }
            
            client = builder.build();
            clientCache.put(knowledgeBaseId, client);
            lastUrlCache.put(knowledgeBaseId, currentUrl);
            lastApiKeyCache.put(knowledgeBaseId, currentApiKey);
            
            logger.debug("为知识库创建Weaviate WebClient - 知识库ID: {}, 类型: {}, URL: {}", 
                    knowledgeBaseId, vectorStoreType, currentUrl);
        }
        return client;
    }
    
    /**
     * 获取知识库的向量存储类型
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        if (knowledgeBaseRepository == null) {
            return "weaviate"; // 默认
        }
        try {
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .map(kb -> {
                        String type = kb.getVectorStoreType();
                        if (type != null && "weaviate".equalsIgnoreCase(type)) {
                            return type;
                        }
                        return "weaviate"; // 默认
                    })
                    .orElse("weaviate");
        } catch (Exception e) {
            logger.warn("获取知识库向量存储类型失败，使用默认值weaviate - 知识库ID: {}", knowledgeBaseId, e);
            return "weaviate";
        }
    }
    
    /**
     * 根据类型获取配置
     */
    private com.github.app.dify.appknowledgebase.domain.VectorDatabase getConfigByType(String type) {
        if (vectorDatabaseRepository == null) {
            return null;
        }
        try {
            // 先尝试查找默认的启用配置
            Optional<com.github.app.dify.appknowledgebase.domain.VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType(type);
            if (defaultConfig.isPresent()) {
                return defaultConfig.get();
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<com.github.app.dify.appknowledgebase.domain.VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
            if (!enabledConfigs.isEmpty()) {
                return enabledConfigs.get(0);
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
            List<com.github.app.dify.appknowledgebase.domain.VectorDatabase> allConfigs = vectorDatabaseRepository.findByType(type);
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
        com.github.app.dify.appknowledgebase.domain.VectorDatabase config = getConfigByType("weaviate");
        if (config != null && config.getTimeout() != null) {
            return config.getTimeout();
        }
        return weaviateConfig.getTimeout();
    }
    
    /**
     * 获取或创建知识库对应的类（Class）
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String className = getClassName(knowledgeBaseId);
        
        try {
            // 检查类是否存在
            if (classExists(knowledgeBaseId, className)) {
                logger.debug("Weaviate类已存在 - 知识库ID: {}, 类名: {}", knowledgeBaseId, className);
                return;
            }
            
            // 创建类
            createClass(knowledgeBaseId, className, vectorSize);
            
            logger.info("创建Weaviate类成功 - 知识库ID: {}, 类名: {}, 向量维度: {}", 
                    knowledgeBaseId, className, vectorSize);
            
        } catch (Exception e) {
            logger.error("确保Weaviate类存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("确保Weaviate类存在失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查类是否存在
     */
    private boolean classExists(Long knowledgeBaseId, String className) {
        try {
            Map<String, Object> response = getWebClient(knowledgeBaseId)
                    .get()
                    .uri("/v1/schema/" + className)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                if (clientResponse.statusCode().value() == 404) {
                                    return Mono.empty();
                                }
                                return Mono.error(new RuntimeException(
                                        "检查类存在性失败: HTTP " + clientResponse.statusCode()));
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            return response != null;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            logger.warn("检查类存在性失败 - 类名: {}, HTTP状态: {}", className, e.getStatusCode());
            return false;
        } catch (Exception e) {
            logger.warn("检查类存在性失败 - 类名: {}", className, e);
            return false;
        }
    }
    
    /**
     * 创建类
     */
    private void createClass(Long knowledgeBaseId, String className, int vectorSize) {
        try {
            Map<String, Object> classDefinition = new HashMap<>();
            classDefinition.put("class", className);
            classDefinition.put("description", "Knowledge base class: " + className);
            
            // 配置向量化器：不使用内置向量化，我们自己提供向量
            classDefinition.put("vectorizer", "none");
            
            // 定义属性
            List<Map<String, Object>> properties = new ArrayList<>();
            
            // document_id属性
            // Weaviate不支持long类型，使用number类型（可以存储大整数）
            Map<String, Object> docIdProp = new HashMap<>();
            docIdProp.put("name", "documentId");
            docIdProp.put("dataType", Arrays.asList("number"));
            properties.add(docIdProp);
            
            // chunk_index属性
            Map<String, Object> chunkIndexProp = new HashMap<>();
            chunkIndexProp.put("name", "chunkIndex");
            chunkIndexProp.put("dataType", Arrays.asList("int"));
            properties.add(chunkIndexProp);
            
            // text属性
            Map<String, Object> textProp = new HashMap<>();
            textProp.put("name", "text");
            textProp.put("dataType", Arrays.asList("text"));
            properties.add(textProp);
            
            // knowledge_base_id属性
            // Weaviate不支持long类型，使用number类型（可以存储大整数）
            Map<String, Object> kbIdProp = new HashMap<>();
            kbIdProp.put("name", "knowledgeBaseId");
            kbIdProp.put("dataType", Arrays.asList("number"));
            properties.add(kbIdProp);
            
            classDefinition.put("properties", properties);
            
            // 向量索引配置（可选，使用默认的HNSW索引）
            // Weaviate默认使用HNSW索引，通常不需要显式配置
            // 如果需要自定义，可以添加vectorIndexConfig
            
            logger.debug("创建类请求 - 类名: {}, 请求体: {}", className, classDefinition);
            
            getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/v1/schema")
                    .bodyValue(classDefinition)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "创建类请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("创建类请求失败 - 类名: {}, HTTP状态: {}, 错误响应: {}", 
                                            className, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
        } catch (Exception e) {
            logger.error("创建Weaviate类失败 - 类名: {}", className, e);
            throw new RuntimeException("创建Weaviate类失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量插入/更新向量
     */
    @Override
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                               List<List<Float>> vectors, List<String> texts, 
                               List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            logger.warn("向量列表为空，跳过插入 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        String className = getClassName(knowledgeBaseId);
        int vectorSize = vectors.get(0).size();
        
        // 确保类存在
        ensureCollection(knowledgeBaseId, vectorSize);
        
        try {
            // 先删除该文档的旧向量
            deleteDocumentVectors(knowledgeBaseId, documentId);
            
            // 分批插入，每批最多100个对象（避免响应过大）
            int batchSize = 100;
            int totalBatches = (vectors.size() + batchSize - 1) / batchSize;
            int totalSuccess = 0;
            int totalErrors = 0;
            
            for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
                int start = batchIndex * batchSize;
                int end = Math.min(start + batchSize, vectors.size());
                
                // 准备当前批次的数据
                List<Map<String, Object>> objects = new ArrayList<>();
                
                for (int i = start; i < end; i++) {
                    List<Float> vector = vectors.get(i);
                    String text = i < texts.size() ? texts.get(i) : "";
                    int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                    
                    // 生成唯一ID（使用UUID v5格式，基于命名空间和内容生成确定性的UUID）
                    String objectId = generateObjectId(knowledgeBaseId, documentId, chunkIndex);
                    
                    // Weaviate批量插入格式：每个对象包含class、id、vector和properties
                    Map<String, Object> object = new HashMap<>();
                    object.put("class", className);
                    object.put("id", objectId); // Weaviate要求ID必须是UUID格式
                    object.put("vector", vector); // 向量数组
                    
                    // 属性
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("documentId", documentId);
                    properties.put("chunkIndex", chunkIndex);
                    properties.put("text", text);
                    properties.put("knowledgeBaseId", knowledgeBaseId);
                    object.put("properties", properties);
                    
                    objects.add(object);
                }
                
                Map<String, Object> batchRequest = new HashMap<>();
                batchRequest.put("objects", objects);
                
                logger.debug("发送向量插入请求（批次 {}/{}） - 知识库ID: {}, 文档ID: {}, 类名: {}, 向量数量: {}", 
                        batchIndex + 1, totalBatches, knowledgeBaseId, documentId, className, objects.size());
                
                try {
                    // Weaviate批量插入API返回的是一个数组，每个元素代表一个对象的插入结果
                    List<Map<String, Object>> response = getWebClient(knowledgeBaseId)
                            .post()
                            .uri("/v1/batch/objects")
                            .bodyValue(batchRequest)
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
                                            logger.error("向量插入请求失败 - 知识库ID: {}, 文档ID: {}, 类名: {}, HTTP状态: {}, 错误响应: {}", 
                                                    knowledgeBaseId, documentId, className, clientResponse.statusCode(), errorBody);
                                            return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                        });
                                    })
                            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                            .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                            .block();
                    
                    // 检查响应中是否有错误
                    if (response != null && !response.isEmpty()) {
                        // 检查是否有错误的对象
                        int successCount = 0;
                        int errorCount = 0;
                        for (Map<String, Object> item : response) {
                            // Weaviate的响应格式：每个对象可能包含 "result" 或 "errors" 字段
                            if (item.containsKey("errors")) {
                                errorCount++;
                                @SuppressWarnings("unchecked")
                                Map<String, Object> errors = (Map<String, Object>) item.get("errors");
                                logger.warn("向量插入部分失败（批次 {}/{}） - 知识库ID: {}, 文档ID: {}, 错误: {}", 
                                        batchIndex + 1, totalBatches, knowledgeBaseId, documentId, errors);
                            } else if (item.containsKey("result")) {
                                // 成功插入的对象通常包含 "result" 字段
                                successCount++;
                            } else {
                                // 如果没有错误字段，认为插入成功
                                successCount++;
                            }
                        }
                        totalSuccess += successCount;
                        totalErrors += errorCount;
                        logger.debug("批次 {}/{} 完成 - 知识库ID: {}, 文档ID: {}, 成功: {}, 失败: {}", 
                                batchIndex + 1, totalBatches, knowledgeBaseId, documentId, successCount, errorCount);
                    } else {
                        // 如果响应为空，假设所有对象都成功插入
                        totalSuccess += objects.size();
                        logger.debug("批次 {}/{} 完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {} (响应为空，假设全部成功)", 
                                batchIndex + 1, totalBatches, knowledgeBaseId, documentId, objects.size());
                    }
                } catch (Exception e) {
                    logger.error("批次 {}/{} 插入失败 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                            batchIndex + 1, totalBatches, knowledgeBaseId, documentId, objects.size(), e);
                    totalErrors += objects.size();
                    // 继续处理下一批，不中断整个流程
                }
            }
            
            logger.info("向量插入请求完成 - 知识库ID: {}, 文档ID: {}, 总数量: {}, 成功: {}, 失败: {}", 
                    knowledgeBaseId, documentId, vectors.size(), totalSuccess, totalErrors);
            
            if (totalErrors > 0) {
                logger.warn("向量插入部分失败 - 知识库ID: {}, 文档ID: {}, 失败数量: {}", 
                        knowledgeBaseId, documentId, totalErrors);
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
        String className = getClassName(knowledgeBaseId);
        
        // 检查类是否存在
        if (!classExists(knowledgeBaseId, className)) {
            logger.warn("Weaviate类不存在 - 知识库ID: {}, 类名: {}, 返回空结果", 
                    knowledgeBaseId, className);
            return new ArrayList<>();
        }
        
        // 检查查询向量是否为空或维度为0
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }
        
        try {
            // 使用GraphQL进行向量搜索
            // Weaviate的GraphQL查询需要使用JSON格式的向量数组
            // 注意：Weaviate的nearVector参数需要vector数组，不能直接使用字符串拼接
            // 我们需要使用JSON格式的查询
            String vectorJson = vectorToString(queryVector);
            
            // 构建GraphQL查询字符串
            // 注意：Weaviate的GraphQL查询中，向量数组需要作为JSON字符串传递
            String graphqlQuery = String.format(
                "{ Get { %s(nearVector: {vector: %s}, limit: %d) { _additional { id certainty distance } documentId chunkIndex text knowledgeBaseId } } }",
                className,
                vectorJson,
                topK
            );
            
            Map<String, Object> query = new HashMap<>();
            query.put("query", graphqlQuery);
            
            logger.debug("发送Weaviate搜索请求 - 知识库ID: {}, 类名: {}, 向量维度: {}, topK: {}", 
                    knowledgeBaseId, className, queryVector.size(), topK);
            
            Map<String, Object> response = getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/v1/graphql")
                    .bodyValue(query)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "Weaviate搜索请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("Weaviate搜索请求失败 - 知识库ID: {}, 类名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, className, clientResponse.statusCode(), errorBody);
                                    return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                });
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();
            
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data.containsKey("Get")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> get = (Map<String, Object>) data.get("Get");
                    if (get.containsKey(className)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> objects = (List<Map<String, Object>>) get.get(className);
                        
                        for (Map<String, Object> obj : objects) {
                        VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();
                        
                        // 获取相似度分数（certainty或distance）
                        // Weaviate的certainty范围是0-1，distance范围取决于距离函数
                        // 我们使用certainty作为相似度分数，如果没有certainty则使用1-distance
                        @SuppressWarnings("unchecked")
                        Map<String, Object> additional = (Map<String, Object>) obj.get("_additional");
                        if (additional != null) {
                            Object certaintyObj = additional.get("certainty");
                            if (certaintyObj != null && certaintyObj instanceof Number) {
                                result.setScore(((Number) certaintyObj).doubleValue());
                            } else {
                                // 如果没有certainty，尝试使用distance（距离越小，相似度越高）
                                Object distanceObj = additional.get("distance");
                                if (distanceObj != null && distanceObj instanceof Number) {
                                    double distance = ((Number) distanceObj).doubleValue();
                                    // 将距离转换为相似度（假设使用余弦距离，范围0-2）
                                    result.setScore(Math.max(0.0, 1.0 - (distance / 2.0)));
                                } else {
                                    result.setScore(0.0);
                                }
                            }
                        }
                            
                            // 获取属性
                            Object docIdObj = obj.get("documentId");
                            if (docIdObj instanceof Number) {
                                result.setDocumentId(((Number) docIdObj).longValue());
                            }
                            
                            Object chunkIndexObj = obj.get("chunkIndex");
                            if (chunkIndexObj instanceof Number) {
                                result.setChunkIndex(((Number) chunkIndexObj).intValue());
                            }
                            
                            Object textObj = obj.get("text");
                            if (textObj != null) {
                                result.setText(textObj.toString());
                            }
                            
                            results.add(result);
                        }
                    }
                }
            }
            
            logger.debug("向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}", 
                    knowledgeBaseId, topK, results.size());
            
            return results;
            
        } catch (WebClientResponseException e) {
            logger.error("向量检索失败 - 知识库ID: {}, 类名: {}, HTTP状态: {}, 响应体: {}", 
                    knowledgeBaseId, className, e.getStatusCode(), e.getResponseBodyAsString(), e);
            
            // 如果是404，说明类不存在，返回空结果
            if (e.getStatusCode().value() == 404) {
                logger.warn("Weaviate类不存在 - 知识库ID: {}, 类名: {}, 返回空结果", 
                        knowledgeBaseId, className);
                return new ArrayList<>();
            }
            
            throw new RuntimeException("向量检索失败: " + e.getMessage() + 
                    (e.getResponseBodyAsString() != null ? " - " + e.getResponseBodyAsString() : ""), e);
        } catch (Exception e) {
            logger.error("向量检索失败 - 知识库ID: {}, 类名: {}", knowledgeBaseId, className, e);
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String className = getClassName(knowledgeBaseId);
        
        // 先检查类是否存在，如果不存在则无需删除
        if (!classExists(knowledgeBaseId, className)) {
            logger.info("类不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 类名: {}", 
                    knowledgeBaseId, documentId, className);
            return;
        }
        
        try {
            // 使用GraphQL查询该文档的所有对象ID
            // Weaviate的where过滤器格式：使用where参数进行过滤
            // 注意：documentId是number类型，使用valueNumber而不是valueInt
            String graphqlQuery = String.format(
                "{ Get { %s(where: {path: [\"documentId\"], operator: Equal, valueNumber: %d}) { _additional { id } } } }",
                className,
                documentId
            );
            
            logger.debug("查询文档向量 - 知识库ID: {}, 文档ID: {}, GraphQL查询: {}", 
                    knowledgeBaseId, documentId, graphqlQuery);
            
            Map<String, Object> query = new HashMap<>();
            query.put("query", graphqlQuery);
            
            Map<String, Object> response = getWebClient(knowledgeBaseId)
                    .post()
                    .uri("/v1/graphql")
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                    .block();
            
            List<String> objectIds = new ArrayList<>();
            
            if (response != null && response.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data.containsKey("Get")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> get = (Map<String, Object>) data.get("Get");
                    if (get.containsKey(className)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> objects = (List<Map<String, Object>>) get.get(className);
                        
                        for (Map<String, Object> obj : objects) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> additional = (Map<String, Object>) obj.get("_additional");
                            if (additional != null) {
                                Object idObj = additional.get("id");
                                if (idObj != null) {
                                    objectIds.add(idObj.toString());
                                }
                            }
                        }
                    }
                }
            }
            
            if (objectIds.isEmpty()) {
                logger.info("未找到文档向量，跳过删除操作 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
                return;
            }
            
            // 批量删除对象
            // Weaviate支持批量删除，使用batch API更高效
            // 如果对象数量较少，也可以使用单个删除API
            if (objectIds.size() <= 10) {
                // 对象数量较少，使用单个删除API
                for (String objectId : objectIds) {
                    try {
                        getWebClient(knowledgeBaseId)
                                .delete()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/v1/objects/{className}/{id}")
                                        .build(className, objectId))
                                .retrieve()
                                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                        clientResponse -> {
                                            // 如果是404，说明对象不存在，这是正常情况
                                            if (clientResponse.statusCode().value() == 404) {
                                                return Mono.empty();
                                            }
                                            Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                                    .defaultIfEmpty("");
                                            
                                            return errorBodyMono.flatMap(errorBody -> {
                                                String errorMsg = "删除向量请求失败: HTTP " + clientResponse.statusCode();
                                                if (errorBody != null && !errorBody.isEmpty()) {
                                                    errorMsg += " - " + errorBody;
                                                }
                                                logger.error("删除向量请求失败 - 知识库ID: {}, 文档ID: {}, 对象ID: {}, HTTP状态: {}, 错误响应: {}", 
                                                        knowledgeBaseId, documentId, objectId, clientResponse.statusCode(), errorBody);
                                                return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                            });
                                        })
                                .bodyToMono(String.class)
                                .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                                .block();
                    } catch (WebClientResponseException e) {
                        // 如果是404，说明对象不存在，这是正常情况
                        if (e.getStatusCode().value() == 404) {
                            logger.debug("对象不存在，跳过删除 - 对象ID: {}", objectId);
                            continue;
                        }
                        logger.warn("删除对象失败 - 对象ID: {}, HTTP状态: {}", objectId, e.getStatusCode());
                    }
                }
            } else {
                // 对象数量较多，使用批量删除（通过GraphQL批量删除）
                // 注意：Weaviate的批量删除需要使用batch API或GraphQL
                // 这里使用GraphQL批量删除
                // 注意：documentId是number类型，使用valueNumber而不是valueInt
                String deleteQuery = String.format(
                    "mutation { Delete { %s(where: {path: [\"documentId\"], operator: Equal, valueNumber: %d}) { n } } }",
                    className,
                    documentId
                );
                
                Map<String, Object> deleteRequest = new HashMap<>();
                deleteRequest.put("query", deleteQuery);
                
                try {
                    Map<String, Object> deleteResponse = getWebClient(knowledgeBaseId)
                            .post()
                            .uri("/v1/graphql")
                            .bodyValue(deleteRequest)
                            .retrieve()
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                    clientResponse -> {
                                        Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                                .defaultIfEmpty("");
                                        
                                        return errorBodyMono.flatMap(errorBody -> {
                                            String errorMsg = "批量删除向量请求失败: HTTP " + clientResponse.statusCode();
                                            if (errorBody != null && !errorBody.isEmpty()) {
                                                errorMsg += " - " + errorBody;
                                            }
                                            logger.error("批量删除向量请求失败 - 知识库ID: {}, 文档ID: {}, HTTP状态: {}, 错误响应: {}", 
                                                    knowledgeBaseId, documentId, clientResponse.statusCode(), errorBody);
                                            return Mono.<RuntimeException>error(new RuntimeException(errorMsg));
                                        });
                                    })
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                            .block();
                    
                    if (deleteResponse != null && deleteResponse.containsKey("data")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) deleteResponse.get("data");
                        if (data.containsKey("Delete")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> delete = (Map<String, Object>) data.get("Delete");
                            if (delete.containsKey(className)) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> result = (Map<String, Object>) delete.get(className);
                                Object nObj = result.get("n");
                                if (nObj instanceof Number) {
                                    int deletedCount = ((Number) nObj).intValue();
                                    logger.debug("批量删除完成 - 知识库ID: {}, 文档ID: {}, 删除数量: {}", 
                                            knowledgeBaseId, documentId, deletedCount);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("批量删除失败，回退到单个删除 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
                    // 回退到单个删除
                    for (String objectId : objectIds) {
                        try {
                            getWebClient(knowledgeBaseId)
                                    .delete()
                                    .uri(uriBuilder -> uriBuilder
                                            .path("/v1/objects/{className}/{id}")
                                            .build(className, objectId))
                                    .retrieve()
                                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                            clientResponse -> {
                                                if (clientResponse.statusCode().value() == 404) {
                                                    return Mono.empty();
                                                }
                                                return Mono.error(new RuntimeException(
                                                        "删除对象失败: HTTP " + clientResponse.statusCode()));
                                            })
                                    .bodyToMono(String.class)
                                    .timeout(Duration.ofMillis(getTimeout(knowledgeBaseId)))
                                    .block();
                        } catch (Exception ex) {
                            logger.debug("删除对象失败 - 对象ID: {}", objectId, ex);
                        }
                    }
                }
            }
            
            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}, 删除数量: {}", 
                    knowledgeBaseId, documentId, objectIds.size());
            
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("删除文档向量失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取类名称
     */
    private String getClassName(Long knowledgeBaseId) {
        // Weaviate类名必须以大写字母开头，且只能包含字母和数字
        return "Kb" + knowledgeBaseId;
    }
    
    /**
     * 生成对象ID（使用UUID v5格式，基于命名空间和内容生成确定性的UUID）
     * Weaviate要求ID必须是UUID格式
     */
    private String generateObjectId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        // 使用UUID v5（基于命名空间的UUID）生成确定性的UUID
        // 命名空间UUID：固定值，用于确保不同应用之间的UUID不会冲突
        UUID namespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"); // DNS命名空间
        
        // 生成唯一字符串：knowledgeBaseId_documentId_chunkIndex
        String name = knowledgeBaseId + "_" + documentId + "_" + chunkIndex;
        
        // 使用UUID v5算法生成确定性的UUID
        return generateUUIDv5(namespace, name).toString();
    }
    
    /**
     * 生成UUID v5（基于命名空间和名称）
     * UUID v5使用SHA-1哈希算法
     */
    private UUID generateUUIDv5(UUID namespace, String name) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            
            // 将命名空间UUID转换为字节数组（16字节）
            long msb = namespace.getMostSignificantBits();
            long lsb = namespace.getLeastSignificantBits();
            byte[] namespaceBytes = new byte[16];
            for (int i = 0; i < 8; i++) {
                namespaceBytes[i] = (byte) (msb >>> (8 * (7 - i)));
            }
            for (int i = 0; i < 8; i++) {
                namespaceBytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
            }
            
            // 将名称转换为字节数组
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            
            // 计算SHA-1哈希：namespace + name
            sha1.update(namespaceBytes);
            sha1.update(nameBytes);
            byte[] hash = sha1.digest();
            
            // 将哈希转换为UUID格式
            // UUID v5版本号：将第6-7字节的最高4位设置为5
            hash[6] &= 0x0f;  // 清除版本位
            hash[6] |= 0x50;  // 设置版本为5
            
            // UUID v5变体：将第8字节的最高2位设置为10
            hash[8] &= 0x3f;  // 清除变体位
            hash[8] |= 0x80;  // 设置变体为10
            
            // 将字节数组转换为UUID
            long msbResult = 0;
            long lsbResult = 0;
            for (int i = 0; i < 8; i++) {
                msbResult = (msbResult << 8) | (hash[i] & 0xff);
            }
            for (int i = 8; i < 16; i++) {
                lsbResult = (lsbResult << 8) | (hash[i] & 0xff);
            }
            
            return new UUID(msbResult, lsbResult);
        } catch (NoSuchAlgorithmException e) {
            // 如果SHA-1不可用，回退到随机UUID（但这样就不是确定性的了）
            logger.warn("SHA-1不可用，使用随机UUID", e);
            return UUID.randomUUID();
        }
    }
    
    /**
     * 将向量转换为字符串（用于GraphQL查询）
     */
    private String vectorToString(List<Float> vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 检索结果
     */
}