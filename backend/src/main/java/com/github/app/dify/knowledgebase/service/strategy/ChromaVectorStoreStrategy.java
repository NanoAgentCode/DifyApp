package com.github.app.dify.knowledgebase.service.strategy;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.system.config.ChromaConfig;
import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
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
 * Chroma向量存储服务（使用HTTP REST API）
 */
@Service
public class ChromaVectorStoreStrategy implements VectorStoreStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ChromaVectorStoreStrategy.class);
    
    @Override
    public String getType() {
        return "chroma";
    }
    
    @Autowired
    private ChromaConfig chromaConfig;
    
    
    private WebClient webClient;
    private String lastUrl;
    private String lastApiKey;
    
    // Chroma API 版本（v1 已废弃，必须使用 v2）
    private static final String API_VERSION = "v2";
    
    // 默认 tenant 和 database（从 /api/v2/auth/identity 获取）
    private String defaultTenant = "default_tenant";
    private String defaultDatabase = "default_database";
    
    // 集合名称到 collection_id 的缓存（ConcurrentHashMap 保证多线程安全）
    private final java.util.concurrent.ConcurrentHashMap<String, String> collectionIdCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 获取默认的 tenant 和 database
     */
    private void ensureTenantAndDatabase() {
        try {
            Map<String, Object> identity = getWebClient()
                    .get()
                    .uri("/api/" + API_VERSION + "/auth/identity")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            if (identity != null) {
                Object tenantObj = identity.get("tenant");
                if (tenantObj != null) {
                    defaultTenant = tenantObj.toString();
                }
                
                @SuppressWarnings("unchecked")
                List<String> databases = (List<String>) identity.get("databases");
                if (databases != null && !databases.isEmpty()) {
                    defaultDatabase = databases.get(0);
                }
                
                logger.debug("获取到默认 tenant: {}, database: {}", defaultTenant, defaultDatabase);
            }
        } catch (Exception e) {
            logger.warn("获取 tenant 和 database 失败，使用默认值 - tenant: {}, database: {}, 错误: {}", 
                    defaultTenant, defaultDatabase, e.getMessage());
        }
    }
    
    /**
     * 通过集合名称获取或创建集合，返回 collection_id
     */
    private String getOrCreateCollectionId(String collectionName) {
        // 先检查缓存
        if (collectionIdCache.containsKey(collectionName)) {
            return collectionIdCache.get(collectionName);
        }
        
        ensureTenantAndDatabase();
        
        try {
            // 先尝试列出所有集合，查找是否存在
            List<Map<String, Object>> collections = getWebClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections")
                            .build(defaultTenant, defaultDatabase))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            if (collections != null) {
                for (Map<String, Object> collection : collections) {
                    Object name = collection.get("name");
                    Object id = collection.get("id");
                    if (collectionName.equals(name) && id != null) {
                        String collectionId = id.toString();
                        collectionIdCache.put(collectionName, collectionId);
                        logger.debug("找到已存在的集合 - 名称: {}, ID: {}", collectionName, collectionId);
                        return collectionId;
                    }
                }
            }
            
            // 集合不存在，创建新集合
            Map<String, Object> createRequest = new HashMap<>();
            createRequest.put("name", collectionName);
            createRequest.put("get_or_create", true);
            
            Map<String, Object> collection = getWebClient()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections")
                            .build(defaultTenant, defaultDatabase))
                    .bodyValue(createRequest)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            if (collection != null && collection.get("id") != null) {
                String collectionId = collection.get("id").toString();
                collectionIdCache.put(collectionName, collectionId);
                logger.info("创建集合成功 - 名称: {}, ID: {}", collectionName, collectionId);
                return collectionId;
            }
            
            throw new BusinessException("创建集合失败，未返回 collection_id", ErrorCode.DATABASE_CONNECTION_ERROR);
            
        } catch (WebClientResponseException e) {
            // 如果是 409 或 400（已存在），尝试再次查找
            if (e.getStatusCode().value() == 409 || e.getStatusCode().value() == 400) {
                logger.debug("集合可能已存在，尝试查找 - 名称: {}", collectionName);
                // 重新查找
                List<Map<String, Object>> collections = getWebClient()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections")
                                .build(defaultTenant, defaultDatabase))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                        .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                        .block();
                
                if (collections != null) {
                    for (Map<String, Object> collection : collections) {
                        Object name = collection.get("name");
                        Object id = collection.get("id");
                        if (collectionName.equals(name) && id != null) {
                            String collectionId = id.toString();
                            collectionIdCache.put(collectionName, collectionId);
                            logger.debug("找到已存在的集合 - 名称: {}, ID: {}", collectionName, collectionId);
                            return collectionId;
                        }
                    }
                }
            }
            throw new BusinessException("获取或创建集合失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }
    
    private WebClient getWebClient() {
        // 检查配置是否发生变化，如果变化则重新创建WebClient
        String currentUrl = chromaConfig.getUrl();
        String currentApiKey = chromaConfig.getApiKey();
        
        if (webClient == null || 
            !currentUrl.equals(lastUrl) || 
            (currentApiKey != null ? !currentApiKey.equals(lastApiKey) : lastApiKey != null)) {
            // 配置连接池和超时
            reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(300)) // 5分钟响应超时
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30秒连接超时
                    .doOnConnected(conn -> {
                        // 设置读取超时
                        conn.addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(300, java.util.concurrent.TimeUnit.SECONDS));
                    });
            
            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(currentUrl)
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            if (currentApiKey != null && !currentApiKey.trim().isEmpty()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + currentApiKey);
            }
            
            webClient = builder.build();
            lastUrl = currentUrl;
            lastApiKey = currentApiKey;
            logger.debug("重新创建Chroma WebClient - URL: {}", currentUrl);
        }
        return webClient;
    }
    
    /**
     * 获取或创建知识库对应的集合
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 获取或创建集合，获取 collection_id
            String collectionId = getOrCreateCollectionId(collectionName);
            logger.info("确保集合存在成功 - 知识库ID: {}, 集合名: {}, 集合ID: {}, 向量维度: {}", 
                    knowledgeBaseId, collectionName, collectionId, vectorSize);
        } catch (Exception e) {
            logger.error("确保Chroma集合存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new BusinessException("确保Chroma集合存在失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }
    
    /**
     * 批量插入向量
     * 如果向量数量较多，会分批插入以避免请求体过大
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
        
        // 确保集合存在
        ensureCollection(knowledgeBaseId, vectorSize);
        
        // 分批插入，每批最多 10 个向量（避免请求体过大导致连接关闭）
        // 注意：Chroma 可能对请求体大小有限制，使用较小的批次更安全
        int batchSize = 10;
        int totalBatches = (vectors.size() + batchSize - 1) / batchSize;
        
        logger.debug("开始分批插入向量 - 知识库ID: {}, 文档ID: {}, 总向量数: {}, 批次数: {}", 
                knowledgeBaseId, documentId, vectors.size(), totalBatches);
        
        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            int start = batchIndex * batchSize;
            int end = Math.min(start + batchSize, vectors.size());
            
            List<List<Float>> batchVectors = vectors.subList(start, end);
            List<String> batchTexts = texts.subList(start, Math.min(start + batchSize, texts.size()));
            List<Integer> batchChunkIndices = chunkIndices.subList(start, Math.min(start + batchSize, chunkIndices.size()));
            
            try {
                insertBatch(knowledgeBaseId, documentId, collectionName, 
                           batchVectors, batchTexts, batchChunkIndices, 
                           batchIndex + 1, totalBatches);
            } catch (Exception e) {
                logger.error("批次插入失败 - 知识库ID: {}, 文档ID: {}, 批次: {}/{}", 
                        knowledgeBaseId, documentId, batchIndex + 1, totalBatches, e);
                throw new BusinessException("向量插入失败（批次 " + (batchIndex + 1) + "/" + totalBatches + "）", ErrorCode.DATABASE_CONNECTION_ERROR, e);
            }
        }
        
        logger.info("向量插入完成 - 知识库ID: {}, 文档ID: {}, 总向量数: {}, 批次数: {}", 
                knowledgeBaseId, documentId, vectors.size(), totalBatches);
    }
    
    /**
     * 插入一批向量
     */
    private void insertBatch(Long knowledgeBaseId, Long documentId, String collectionName,
                             List<List<Float>> vectors, List<String> texts, 
                             List<Integer> chunkIndices, int batchNum, int totalBatches) {
        try {
            // 准备数据
            List<String> ids = new ArrayList<>();
            List<List<Float>> embeddings = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            
            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                
                // 生成唯一ID
                String id = generateId(knowledgeBaseId, documentId, chunkIndex);
                ids.add(id);
                
                embeddings.add(vector);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", documentId);
                metadata.put("chunk_index", chunkIndex);
                metadata.put("text", text);
                metadata.put("knowledge_base_id", knowledgeBaseId);
                metadatas.add(metadata);
            }
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ids", ids);
            requestBody.put("embeddings", embeddings);
            requestBody.put("metadatas", metadatas);
            requestBody.put("documents", texts);
            
            logger.debug("发送向量插入请求（批次 {}/{}）- 知识库ID: {}, 文档ID: {}, 集合名: {}, 向量数量: {}", 
                    batchNum, totalBatches, knowledgeBaseId, documentId, collectionName, vectors.size());
            
            // 获取 collection_id
            String collectionId = getOrCreateCollectionId(collectionName);
            
            // 根据批次大小动态调整超时时间（每个向量约 1 秒）
            int timeout = Math.max(chromaConfig.getTimeout(), vectors.size() * 1000);
            
            // 重试机制：最多重试 3 次
            int maxRetries = 3;
            Exception lastException = null;
            
            for (int retry = 0; retry < maxRetries; retry++) {
                try {
                    getWebClient()
                            .post()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections/{collection_id}/add")
                                    .build(defaultTenant, defaultDatabase, collectionId))
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
                                            return Mono.error(new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR));
                                        });
                                    })
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .timeout(Duration.ofMillis(timeout))
                            .block();
                    
                    logger.debug("批次插入成功（批次 {}/{}）- 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                            batchNum, totalBatches, knowledgeBaseId, documentId, vectors.size());
                    return; // 成功，退出重试循环
                    
                } catch (WebClientResponseException e) {
                    // 如果是 405 且是 v1 API，尝试不同的方法或端点
                    // v2 API 如果返回 405，记录详细错误信息
                    if (e.getStatusCode().value() == 405) {
                        logger.error("Chroma v2 API 返回 405 Method Not Allowed - 知识库ID: {}, 文档ID: {}, 集合名: {}, 端点: /api/v2/collections/{}/add, 响应: {}", 
                                knowledgeBaseId, documentId, collectionName, collectionName, e.getResponseBodyAsString());
                        throw new BusinessException("Chroma接口调用失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
                    }
                    // 其他 HTTP 错误，继续抛出
                    lastException = e;
                    throw e;
                } catch (Exception e) {
                    lastException = e;
                    // 如果是连接关闭错误，等待后重试
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("Connection prematurely closed") ||
                         e.getMessage().contains("PrematureCloseException"))) {
                        if (retry < maxRetries - 1) {
                            long waitTime = (retry + 1) * 1000; // 递增等待时间：1s, 2s, 3s
                            logger.warn("连接关闭，等待 {} 毫秒后重试（批次 {}/{}，重试 {}/{}）- 知识库ID: {}, 文档ID: {}", 
                                    waitTime, batchNum, totalBatches, retry + 1, maxRetries, knowledgeBaseId, documentId);
                            try {
                                Thread.sleep(waitTime);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new BusinessException("重试等待被中断", ErrorCode.SYSTEM_BUSY, ie);
                            }
                            continue; // 继续重试
                        }
                    }
                    // 其他错误或重试次数用完，抛出异常
                    throw e;
                }
            }
            
            // 所有重试都失败
            throw new BusinessException("向量插入失败", ErrorCode.DATABASE_CONNECTION_ERROR, lastException);
            
        } catch (Exception e) {
            logger.error("批次插入失败 - 知识库ID: {}, 文档ID: {}, 批次: {}/{}", 
                    knowledgeBaseId, documentId, batchNum, totalBatches, e);
            throw new BusinessException("向量插入失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }
    
    /**
     * 向量检索
     */
    @Override
    public List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 检查查询向量是否为空或维度为0
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }
        
        try {
            // 获取 collection_id
            String collectionId = getOrCreateCollectionId(collectionName);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query_embeddings", List.of(queryVector));
            requestBody.put("n_results", topK);
            requestBody.put("include", Arrays.asList("metadatas", "documents", "distances"));
            
            logger.debug("发送Chroma搜索请求 - 知识库ID: {}, 集合名: {}, 集合ID: {}, 向量维度: {}, topK: {}", 
                    knowledgeBaseId, collectionName, collectionId, queryVector.size(), topK);
            
            Map<String, Object> response = getWebClient()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections/{collection_id}/query")
                            .build(defaultTenant, defaultDatabase, collectionId))
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), 
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "Chroma搜索请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("Chroma搜索请求失败 - 知识库ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.error(new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR));
                                });
                            })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();
            
            if (response != null) {
                // Chroma返回格式：{ "ids": [[...]], "distances": [[...]], "metadatas": [[...]], "documents": [[...]] }
                @SuppressWarnings("unchecked")
                List<List<String>> idsList = (List<List<String>>) response.get("ids");
                @SuppressWarnings("unchecked")
                List<List<Double>> distancesList = (List<List<Double>>) response.get("distances");
                @SuppressWarnings("unchecked")
                List<List<Map<String, Object>>> metadatasList = (List<List<Map<String, Object>>>) response.get("metadatas");
                @SuppressWarnings("unchecked")
                List<List<String>> documentsList = (List<List<String>>) response.get("documents");
                
                if (idsList != null && !idsList.isEmpty() && !idsList.get(0).isEmpty()) {
                    List<String> ids = idsList.get(0);
                    List<Double> distances = distancesList != null && !distancesList.isEmpty() ? distancesList.get(0) : new ArrayList<>();
                    List<Map<String, Object>> metadatas = metadatasList != null && !metadatasList.isEmpty() ? metadatasList.get(0) : new ArrayList<>();
                    List<String> documents = documentsList != null && !documentsList.isEmpty() ? documentsList.get(0) : new ArrayList<>();
                    
                    for (int i = 0; i < ids.size(); i++) {
                        VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();
                        
                        // Chroma使用距离（distance），需要转换为相似度分数（score）
                        // 距离越小，相似度越高，所以使用 1 / (1 + distance) 或 1 - distance（如果distance是归一化的）
                        double distance = i < distances.size() ? distances.get(i) : 0.0;
                        // 假设距离是余弦距离（0-2范围），转换为相似度分数（0-1范围）
                        double score = 1.0 - (distance / 2.0);
                        result.setScore(score);
                        
                        // 从metadata或documents中获取文本
                        if (i < documents.size() && documents.get(i) != null) {
                            result.setText(documents.get(i));
                        } else if (i < metadatas.size() && metadatas.get(i) != null) {
                            Map<String, Object> metadata = metadatas.get(i);
                            Object textObj = metadata.get("text");
                            if (textObj != null) {
                                result.setText(textObj.toString());
                            }
                        }
                        
                        // 从metadata中获取documentId和chunkIndex
                        if (i < metadatas.size() && metadatas.get(i) != null) {
                            Map<String, Object> metadata = metadatas.get(i);
                            Object docIdObj = metadata.get("document_id");
                            if (docIdObj instanceof Number) {
                                result.setDocumentId(((Number) docIdObj).longValue());
                            }
                            
                            Object chunkIndexObj = metadata.get("chunk_index");
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
                logger.warn("Chroma集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                        knowledgeBaseId, collectionName);
                return new ArrayList<>();
            }

            throw new BusinessException("向量检索失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        } catch (Exception e) {
            logger.error("向量检索失败 - 知识库ID: {}, 集合名: {}", knowledgeBaseId, collectionName, e);
            throw new BusinessException("向量检索失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 获取 collection_id
            String collectionId = getOrCreateCollectionId(collectionName);
            
            // 先查询该文档的所有向量ID
            Map<String, Object> queryRequest = new HashMap<>();
            Map<String, Object> where = new HashMap<>();
            where.put("document_id", documentId);
            queryRequest.put("where", where);
            queryRequest.put("include", List.of("ids"));
            
            Map<String, Object> queryResponse = getWebClient()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections/{collection_id}/get")
                            .build(defaultTenant, defaultDatabase, collectionId))
                    .bodyValue(queryRequest)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            if (queryResponse == null) {
                logger.info("未找到文档向量，跳过删除操作 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
                return;
            }
            
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) queryResponse.get("ids");
            if (ids == null || ids.isEmpty()) {
                logger.info("未找到文档向量，跳过删除操作 - 知识库ID: {}, 文档ID: {}", 
                        knowledgeBaseId, documentId);
                return;
            }
            
            // 删除向量
            Map<String, Object> deleteRequest = new HashMap<>();
            deleteRequest.put("ids", ids);
            
            logger.debug("发送删除向量请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 集合ID: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, collectionName, collectionId, ids.size());
            
            getWebClient()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/" + API_VERSION + "/tenants/{tenant}/databases/{database}/collections/{collection_id}/delete")
                            .build(defaultTenant, defaultDatabase, collectionId))
                    .bodyValue(deleteRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                Mono<String> errorBodyMono = clientResponse.bodyToMono(String.class)
                                        .defaultIfEmpty("");
                                
                                return errorBodyMono.flatMap(errorBody -> {
                                    String errorMsg = "删除向量请求失败: HTTP " + clientResponse.statusCode();
                                    if (errorBody != null && !errorBody.isEmpty()) {
                                        errorMsg += " - " + errorBody;
                                    }
                                    logger.error("删除向量请求失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, HTTP状态: {}, 错误响应: {}", 
                                            knowledgeBaseId, documentId, collectionName, clientResponse.statusCode(), errorBody);
                                    return Mono.error(new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR));
                                });
                            })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(chromaConfig.getTimeout()))
                    .block();
            
            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, ids.size());
            
        } catch (WebClientResponseException e) {
            // 如果是400或404错误，可能是没有匹配的点，记录警告但不抛出异常
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                logger.warn("删除向量返回{}错误（可能是没有匹配的点）- 知识库ID: {}, 文档ID: {}, 集合名: {}, 响应: {}", 
                        e.getStatusCode().value(), knowledgeBaseId, documentId, collectionName, e.getResponseBodyAsString());
                // 不抛出异常，因为可能是正常的（没有向量需要删除）
                return;
            }
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}, HTTP状态: {}, 响应: {}", 
                    knowledgeBaseId, documentId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException("删除文档向量失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        } catch (Exception e) {
            logger.error("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new BusinessException("删除文档向量失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
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
    private String generateId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        return knowledgeBaseId + "_" + documentId + "_" + chunkIndex;
    }

}
