package com.github.app.dify.knowledgebase.service.strategy;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.system.config.ElasticsearchConfig;
import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Elasticsearch向量存储策略实现
 */
@Service
public class ElasticsearchVectorStoreStrategy implements VectorStoreStrategy {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchVectorStoreStrategy.class);

    @Override
    public String getType() {
        return "elasticsearch";
    }

    @Override
    public boolean isHybridSearchSupported() {
        return true;
    }

    @Override
    public List<VectorStoreStrategy.SearchResult> hybridSearch(Long knowledgeBaseId, String query,
            List<Float> queryVector, int topK) {
        String indexName = getIndexName(knowledgeBaseId);

        // 检查查询是否为空
        if ((query == null || query.trim().isEmpty()) && (queryVector == null || queryVector.isEmpty())) {
            logger.warn("混合搜索查询和向量均为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }

        // 如果只有查询或只有向量，退化为普通搜索
        if (query == null || query.trim().isEmpty()) {
            return searchVectors(knowledgeBaseId, queryVector, topK);
        }
        if (queryVector == null || queryVector.isEmpty()) {
            // 这里可以实现纯文本搜索，但目前先只返回向量搜索
            return searchVectors(knowledgeBaseId, queryVector, topK);
        }

        try {
            ElasticsearchClient client = getElasticsearchClient(knowledgeBaseId);

            // 构建混合搜索查询：结合文本匹配和向量相似度
            // 使用 script_score 结合文本查询的分数和向量相似度
            Query hybridQuery = Query.of(q -> q
                    .scriptScore(ss -> ss
                            .query(q2 -> q2
                                    .bool(b -> b
                                            .should(s -> s
                                                    .match(m -> m
                                                            .field("text")
                                                            .query(query)))))
                            .script(s -> s
                                    .inline(i -> i
                                            // 综合分数 = BM25分数 + (余弦相似度 + 1) * 权重
                                            .source("_score + (cosineSimilarity(params.query_vector, 'vector') + 1.0) * 10")
                                            .params("query_vector", JsonData.of(queryVector))))));

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(hybridQuery)
                    .size(topK)
                    .source(src -> src
                            .filter(f -> f
                                    .includes("text", "document_id", "chunk_index"))));

            SearchResponse<?> searchResponse = client.search(searchRequest, Map.class);

            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();
            double maxScore = 0;
            for (Hit<?> hit : searchResponse.hits().hits()) {
                Double score = hit.score();
                if (score != null && score > maxScore) {
                    maxScore = score;
                }
            }

            for (Hit<?> hit : searchResponse.hits().hits()) {
                VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();

                // 归一化分数 (混合搜索的分数可能很大)
                Double scoreValue = hit.score();
                double score = maxScore > 0 ? (scoreValue != null ? scoreValue : 0.0) / maxScore : 0.0;
                result.setScore(score);

                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source != null) {
                    if (source.containsKey("text"))
                        result.setText(source.get("text").toString());
                    if (source.containsKey("document_id")) {
                        Object docIdObj = source.get("document_id");
                        if (docIdObj instanceof Number)
                            result.setDocumentId(((Number) docIdObj).longValue());
                    }
                    if (source.containsKey("chunk_index")) {
                        Object chunkIndexObj = source.get("chunk_index");
                        if (chunkIndexObj instanceof Number)
                            result.setChunkIndex(((Number) chunkIndexObj).intValue());
                    }
                }
                results.add(result);
            }

            logger.info("Elasticsearch混合搜索完成 - 知识库ID: {}, topK: {}, 结果数量: {}",
                    knowledgeBaseId, topK, results.size());

            return results;

        } catch (Exception e) {
            logger.error("Elasticsearch混合搜索失败 - 知识库ID: {}, 索引名: {}", knowledgeBaseId, indexName, e);
            // 失败时回退到纯向量搜索
            return searchVectors(knowledgeBaseId, queryVector, topK);
        }
    }

    @Autowired
    private ElasticsearchConfig elasticsearchConfig;

    @Autowired
    private com.github.app.dify.knowledgebase.util.VectorDatabaseConfigHelper configHelper;

    // 为每个知识库缓存Elasticsearch客户端（ConcurrentHashMap 保证多线程安全）
    private final java.util.concurrent.ConcurrentHashMap<Long, ElasticsearchClient> clientCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> lastUrlCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> lastApiKeyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> lastUsernameCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, String> lastPasswordCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 获取指定知识库的Elasticsearch客户端
     */
    private ElasticsearchClient getElasticsearchClient(Long knowledgeBaseId) {
        // 获取对应的配置
        String currentUrl;
        String currentApiKey;
        String currentUsername = null;
        String currentPassword = null;

        VectorDatabase config = configHelper.getConfigByType("elasticsearch");
        if (config != null) {
            currentUrl = config.getUrl();
            currentApiKey = config.getApiKey();
            // 使用工具类提取用户名和密码
            String[] credentials = configHelper.extractUsernamePassword(config);
            if (credentials != null) {
                currentUsername = credentials[0];
                currentPassword = credentials[1];
            }
        } else {
            currentUrl = elasticsearchConfig.getUrl();
            currentApiKey = elasticsearchConfig.getApiKey();
            currentUsername = elasticsearchConfig.getUsername();
            currentPassword = elasticsearchConfig.getPassword();
        }

        // 创建final变量供lambda表达式使用
        final String finalUsername = currentUsername;
        final String finalPassword = currentPassword;
        final String finalApiKey = currentApiKey;

        // 检查缓存
        String lastUrl = lastUrlCache.get(knowledgeBaseId);
        String lastApiKey = lastApiKeyCache.get(knowledgeBaseId);
        String lastUsername = lastUsernameCache.get(knowledgeBaseId);
        String lastPassword = lastPasswordCache.get(knowledgeBaseId);
        ElasticsearchClient client = clientCache.get(knowledgeBaseId);

        if (client == null ||
                !currentUrl.equals(lastUrl) ||
                (!Objects.equals(currentApiKey, lastApiKey)) ||
                (!Objects.equals(currentUsername, lastUsername)) ||
                (!Objects.equals(currentPassword, lastPassword))) {

            // 关闭旧的客户端：关闭底层 Transport（含 RestClient），释放连接
            if (client != null) {
                try {
                    ElasticsearchTransport transport = client._transport();
                    transport.close();
                } catch (Exception e) {
                    logger.warn("关闭旧的Elasticsearch客户端失败", e);
                }
            }

            // 创建新的客户端
            try {
                // 解析URL
                java.net.URI uri = java.net.URI.create(currentUrl);
                java.net.URL urlObj = uri.toURL();
                String host = urlObj.getHost();
                int port = urlObj.getPort() != -1 ? urlObj.getPort() : 9200;
                String scheme = urlObj.getProtocol();

                // 创建RestClient
                RestClientBuilder restClientBuilder = RestClient.builder(
                        new HttpHost(host, port, scheme));

                // 配置HTTP客户端和认证
                restClientBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
                    // 配置认证
                    if (finalUsername != null && finalPassword != null &&
                            !finalUsername.trim().isEmpty() && !finalPassword.trim().isEmpty()) {
                        // 使用Basic Auth
                        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(finalUsername, finalPassword));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                        logger.debug("配置Elasticsearch Basic Auth认证 - 用户名: {}", finalUsername);
                    } else if (finalApiKey != null && !finalApiKey.trim().isEmpty()) {
                        // 使用API Key认证
                        httpClientBuilder
                                .addInterceptorLast((org.apache.http.HttpRequestInterceptor) (request, context) -> request.addHeader("Authorization", "ApiKey " + finalApiKey));
                        logger.debug("配置Elasticsearch API Key认证");
                    }

                    // 注意：不要手动设置 Content-Type 和 Accept 头
                    // Elasticsearch Java Client 8.x 会自动设置正确的媒体类型头部
                    // 手动设置可能导致 media_type_header_exception 错误

                    return httpClientBuilder;
                });

                RestClient restClient = restClientBuilder.build();

                // 创建传输层（不使用兼容模式）
                ElasticsearchTransport transport = new RestClientTransport(
                        restClient,
                        new JacksonJsonpMapper());

                // 创建Elasticsearch客户端
                client = new ElasticsearchClient(transport);

                clientCache.put(knowledgeBaseId, client);
                lastUrlCache.put(knowledgeBaseId, currentUrl);
                lastApiKeyCache.put(knowledgeBaseId, currentApiKey);
                lastUsernameCache.put(knowledgeBaseId, currentUsername);
                lastPasswordCache.put(knowledgeBaseId, currentPassword);

                logger.debug("为知识库创建Elasticsearch客户端 - 知识库ID: {}, URL: {}",
                        knowledgeBaseId, currentUrl);
            } catch (Exception e) {
                logger.error("创建Elasticsearch客户端失败", e);
                throw new BusinessException("创建Elasticsearch客户端失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
            }
        }
        return client;
    }

    /**
     * 确保索引存在
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String indexName = getIndexName(knowledgeBaseId);

        try {
            ElasticsearchClient client = getElasticsearchClient(knowledgeBaseId);

            // 检查索引是否存在
            boolean exists = client.indices().exists(
                    ExistsRequest.of(e -> e.index(indexName))).value();

            if (exists) {
                logger.debug("Elasticsearch索引已存在 - 知识库ID: {}, 索引名: {}",
                        knowledgeBaseId, indexName);
                return;
            }

            // 创建索引
            createIndex(client, indexName, vectorSize);

        } catch (Exception e) {
            logger.error("确保Elasticsearch索引存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new BusinessException("确保Elasticsearch索引存在失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }

    /**
     * 创建索引
     */
    private void createIndex(ElasticsearchClient client, String indexName, int vectorSize) {
        try {
            // 先尝试使用dense_vector类型（Elasticsearch 7.3+支持）
            // 如果失败，将使用float数组作为后备方案
            String mappingJson;

            try {
                // 尝试使用dense_vector类型
                mappingJson = String.format(
                        """
                                {
                                  "properties": {
                                    "vector": {
                                      "type": "dense_vector",
                                      "dims": %d
                                    },
                                    "text": {
                                      "type": "text"
                                    },
                                    "document_id": {
                                      "type": "long"
                                    },
                                    "chunk_index": {
                                      "type": "integer"
                                    },
                                    "knowledge_base_id": {
                                      "type": "long"
                                    }
                                  }
                                }""",
                        vectorSize);

                java.io.ByteArrayInputStream mappingStream = new java.io.ByteArrayInputStream(
                        mappingJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                TypeMapping mapping = TypeMapping.of(m -> m.withJson(mappingStream));

                CreateIndexRequest request = CreateIndexRequest.of(i -> i
                        .index(indexName)
                        .mappings(mapping));

                client.indices().create(request);
                logger.info("创建Elasticsearch索引成功（使用dense_vector类型）- 索引名: {}, 向量维度: {}", indexName, vectorSize);
                return;

            } catch (Exception e) {
                // 如果dense_vector失败，检查是否是类型不支持的错误
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("dense_vector") ||
                        errorMsg.contains("unsupported parameters") ||
                        errorMsg.contains("Unknown type") ||
                        errorMsg.contains("dims"))) {
                    logger.warn("Elasticsearch不支持dense_vector类型，尝试使用float数组作为后备方案 - 索引名: {}, 错误: {}", indexName,
                            errorMsg);
                } else {
                    // 其他错误，直接抛出
                    throw e;
                }
            }

            // 后备方案：如果dense_vector不支持，提供明确的错误提示
            // 注意：Elasticsearch 7.3以下版本不支持dense_vector类型
            // 如果必须使用低版本，建议升级到Elasticsearch 7.3+或8.x
            String errorMsg = String.format(
                    "Elasticsearch服务器不支持dense_vector向量类型。dense_vector类型需要Elasticsearch 7.3或更高版本（推荐8.x）。" +
                            "当前服务器可能版本过低，无法支持向量搜索功能。" +
                            "请升级Elasticsearch到7.3+版本，或使用其他向量数据库（如Qdrant、Milvus等）。" +
                            "索引名: %s, 向量维度: %d",
                    indexName, vectorSize);
            logger.error(errorMsg);
            throw new BusinessException(errorMsg, ErrorCode.DATABASE_CONNECTION_ERROR);

        } catch (Exception e) {
            logger.error("创建Elasticsearch索引失败 - 索引名: {}", indexName, e);
            throw new BusinessException("创建Elasticsearch索引失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
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

        String indexName = getIndexName(knowledgeBaseId);
        int vectorSize = vectors.get(0).size();

        // 确保索引存在
        ensureCollection(knowledgeBaseId, vectorSize);

        try {
            ElasticsearchClient client = getElasticsearchClient(knowledgeBaseId);

            // 先删除该文档的旧向量
            deleteDocumentVectors(knowledgeBaseId, documentId);

            // 准备批量操作
            List<BulkOperation> bulkOperations = new ArrayList<>();

            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;

                // 生成唯一ID
                String docId = generateDocId(knowledgeBaseId, documentId, chunkIndex);

                // 构建文档
                Map<String, Object> doc = new HashMap<>();
                doc.put("vector", vector);
                doc.put("text", text);
                doc.put("document_id", documentId);
                doc.put("chunk_index", chunkIndex);
                doc.put("knowledge_base_id", knowledgeBaseId);

                // 添加到批量操作
                bulkOperations.add(BulkOperation.of(o -> o
                        .index(idx -> idx
                                .index(indexName)
                                .id(docId)
                                .document(JsonData.of(doc)))));
            }

            // 执行批量操作
            BulkRequest bulkRequest = BulkRequest.of(r -> r
                    .operations(bulkOperations)
                    .refresh(Refresh.True));

            BulkResponse bulkResponse = client.bulk(bulkRequest);

            if (bulkResponse.errors()) {
                logger.error("批量插入Elasticsearch向量时发生错误 - 知识库ID: {}, 文档ID: {}",
                        knowledgeBaseId, documentId);
                // 记录错误详情
                bulkResponse.items().forEach(item -> {
                    var error = item.error();
                    if (error != null) {
                        logger.error("批量操作错误: {}", error.reason());
                    }
                });
                throw new BusinessException("批量插入向量时发生错误", ErrorCode.DATABASE_CONNECTION_ERROR);
            }

            logger.info("Elasticsearch向量插入完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {}",
                    knowledgeBaseId, documentId, vectors.size());

        } catch (Exception e) {
            logger.error("向量插入失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new BusinessException("向量插入失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }

    /**
     * 向量检索
     */
    @Override
    public List<VectorStoreStrategy.SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector,
            int topK) {
        String indexName = getIndexName(knowledgeBaseId);

        // 检查查询向量是否为空
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }

        try {
            ElasticsearchClient client = getElasticsearchClient(knowledgeBaseId);

            // 构建向量搜索查询
            Query vectorQuery = Query.of(q -> q
                    .scriptScore(ss -> ss
                            .query(q2 -> q2.matchAll(m -> m))
                            .script(s -> s
                                    .inline(i -> i
                                            .source("cosineSimilarity(params.query_vector, 'vector') + 1.0")
                                            .params("query_vector", JsonData.of(queryVector))))));

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(indexName)
                    .query(vectorQuery)
                    .size(topK)
                    .source(src -> src
                            .filter(f -> f
                                    .includes("text", "document_id", "chunk_index"))));

            SearchResponse<?> searchResponse = client.search(searchRequest, Map.class);

            List<VectorStoreStrategy.SearchResult> results = new ArrayList<>();

            for (Hit<?> hit : searchResponse.hits().hits()) {
                VectorStoreStrategy.SearchResult result = new VectorStoreStrategy.SearchResult();

                // 相似度分数（cosineSimilarity返回-1到1，+1.0后变为0到2，归一化到0到1）
                Double scoreValue = hit.score();
                double score = (scoreValue != null ? scoreValue : 0.0) / 2.0;
                result.setScore(score);

                // 获取字段值
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source != null) {
                    if (source.containsKey("text")) {
                        result.setText(source.get("text").toString());
                    }
                    if (source.containsKey("document_id")) {
                        Object docIdObj = source.get("document_id");
                        if (docIdObj instanceof Number) {
                            result.setDocumentId(((Number) docIdObj).longValue());
                        }
                    }
                    if (source.containsKey("chunk_index")) {
                        Object chunkIndexObj = source.get("chunk_index");
                        if (chunkIndexObj instanceof Number) {
                            result.setChunkIndex(((Number) chunkIndexObj).intValue());
                        }
                    }
                }

                results.add(result);
            }

            logger.debug("向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}",
                    knowledgeBaseId, topK, results.size());

            return results;

        } catch (Exception e) {
            logger.error("向量检索失败 - 知识库ID: {}, 索引名: {}", knowledgeBaseId, indexName, e);
            throw new BusinessException("向量检索失败", ErrorCode.DATABASE_CONNECTION_ERROR, e);
        }
    }

    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String indexName = getIndexName(knowledgeBaseId);

        try {
            ElasticsearchClient client = getElasticsearchClient(knowledgeBaseId);

            // 构建删除查询
            Query deleteQuery = Query.of(q -> q
                    .term(t -> t
                            .field("document_id")
                            .value(documentId)));

            DeleteByQueryRequest deleteRequest = DeleteByQueryRequest.of(d -> d
                    .index(indexName)
                    .query(deleteQuery)
                    .refresh(true));

            DeleteByQueryResponse deleteResponse = client.deleteByQuery(deleteRequest);

            logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}, 删除数量: {}",
                    knowledgeBaseId, documentId, deleteResponse.deleted());

        } catch (Exception e) {
            logger.warn("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            // 删除失败不抛出异常，避免影响其他操作
        }
    }

    /**
     * 获取索引名称
     */
    private String getIndexName(Long knowledgeBaseId) {
        return "kb_" + knowledgeBaseId;
    }

    /**
     * 生成文档ID
     */
    private String generateDocId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        return knowledgeBaseId + "_" + documentId + "_" + chunkIndex;
    }
}
