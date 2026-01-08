package com.github.app.dify.userlog.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.github.app.dify.userlog.document.UserActionLogDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Elasticsearch日志服务
 */
@Service
public class ElasticsearchLogService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchLogService.class);
    private static final String INDEX_NAME = "user_action_logs";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired(required = false)
    private ElasticsearchClient userLogElasticsearchClient;

    @Value("${elasticsearch.enabled:true}")
    private boolean elasticsearchEnabled;

    /**
     * 初始化：检查并创建索引
     */
    @PostConstruct
    public void init() {
        if (!elasticsearchEnabled || userLogElasticsearchClient == null) {
            return;
        }
        
        try {
            // 检查索引是否存在
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
            boolean exists = userLogElasticsearchClient.indices().exists(existsRequest).value();
            
            if (!exists) {
                logger.info("索引 {} 不存在，开始自动创建...", INDEX_NAME);
                createIndex();
            } else {
                logger.info("索引 {} 已存在", INDEX_NAME);
            }
        } catch (Exception e) {
            logger.error("初始化Elasticsearch索引失败", e);
        }
    }

    /**
     * 创建索引
     */
    private void createIndex() {
        try {
            CreateIndexRequest createIndexRequest = CreateIndexRequest.of(c -> c
                .index(INDEX_NAME)
                .settings(IndexSettings.of(s -> s
                    .numberOfShards("1")
                    .numberOfReplicas("1")
                    .maxResultWindow(10000)
                ))
                .mappings(TypeMapping.of(m -> m
                    .properties("id", Property.of(p -> p.keyword(k -> k)))
                    .properties("userId", Property.of(p -> p.long_(l -> l)))
                    .properties("username", Property.of(p -> p.text(t -> t
                        .fields("keyword", Property.of(f -> f.keyword(k -> k)))
                    )))
                    .properties("module", Property.of(p -> p.keyword(k -> k)))
                    .properties("actionType", Property.of(p -> p.keyword(k -> k)))
                    .properties("description", Property.of(p -> p.text(t -> t)))
                    .properties("method", Property.of(p -> p.keyword(k -> k)))
                    .properties("requestPath", Property.of(p -> p.text(t -> t
                        .fields("keyword", Property.of(f -> f.keyword(k -> k)))
                    )))
                    .properties("requestParams", Property.of(p -> p.text(t -> t)))
                    .properties("result", Property.of(p -> p.keyword(k -> k)))
                    .properties("errorMsg", Property.of(p -> p.text(t -> t)))
                    .properties("ipAddress", Property.of(p -> p.ip(i -> i)))
                    .properties("userAgent", Property.of(p -> p.text(t -> t)))
                    .properties("executionTime", Property.of(p -> p.long_(l -> l)))
                    .properties("createTime", Property.of(p -> p.date(d -> d
                        .format("yyyy-MM-dd'T'HH:mm:ss")
                    )))
                ))
            );
            
            userLogElasticsearchClient.indices().create(createIndexRequest);
            logger.info("索引 {} 创建成功", INDEX_NAME);
        } catch (Exception e) {
            logger.error("创建索引失败", e);
        }
    }

    /**
     * 保存日志到Elasticsearch
     */
    public void saveLog(UserActionLogDocument document) {
        if (!elasticsearchEnabled) {
            logger.debug("Elasticsearch未启用，跳过保存日志");
            return;
        }

        try {
            // 生成唯一ID
            if (document.getId() == null) {
                document.setId(UUID.randomUUID().toString());
            }

            // 索引文档
            IndexRequest<UserActionLogDocument> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(document.getId())
                    .document(document)
            );

            if (userLogElasticsearchClient == null) {
                logger.warn("Elasticsearch客户端未初始化，跳过保存日志");
                return;
            }
            
            userLogElasticsearchClient.index(request);
            logger.debug("用户行为日志已保存到Elasticsearch: id={}, userId={}, module={}", 
                    document.getId(), document.getUserId(), document.getModule());
        } catch (Exception e) {
            logger.error("保存用户行为日志到Elasticsearch失败", e);
        }
    }

    /**
     * 搜索日志
     */
    public SearchResult searchLogs(Long userId, String username, String module, 
                                   String actionType, String result,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   int page, int pageSize) {
        if (!elasticsearchEnabled) {
            logger.warn("Elasticsearch未启用，无法查询日志");
            return new SearchResult(new ArrayList<>(), 0);
        }

        try {
            // 构建查询条件
            List<Query> mustQueries = new ArrayList<>();

            if (userId != null) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("userId").value(userId))));
            }
            if (username != null && !username.isEmpty()) {
                mustQueries.add(Query.of(q -> q.wildcard(w -> w.field("username").value("*" + username + "*"))));
            }
            if (module != null && !module.isEmpty()) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("module").value(module))));
            }
            if (actionType != null && !actionType.isEmpty()) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("actionType").value(actionType))));
            }
            if (result != null && !result.isEmpty()) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("result").value(result))));
            }
            if (startTime != null || endTime != null) {
                mustQueries.add(Query.of(q -> q.range(r -> {
                    var rangeBuilder = r.field("createTime");
                    if (startTime != null) {
                        rangeBuilder.gte(co.elastic.clients.json.JsonData.of(startTime.format(FORMATTER)));
                    }
                    if (endTime != null) {
                        rangeBuilder.lte(co.elastic.clients.json.JsonData.of(endTime.format(FORMATTER)));
                    }
                    return rangeBuilder;
                })));
            }

            // 构建布尔查询
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
            if (!mustQueries.isEmpty()) {
                boolQueryBuilder.must(mustQueries);
            }

            // 构建搜索请求
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolQueryBuilder.build()))
                    .from((page - 1) * pageSize)
                    .size(pageSize)
                    .sort(sort -> sort.field(f -> f.field("createTime").order(SortOrder.Desc)))
            );

            // 执行搜索
            if (userLogElasticsearchClient == null) {
                logger.warn("Elasticsearch客户端未初始化，无法查询");
                return new SearchResult(new ArrayList<>(), 0);
            }
            
            SearchResponse<UserActionLogDocument> response = userLogElasticsearchClient.search(
                    searchRequest, 
                    UserActionLogDocument.class
            );

            // 提取结果
            List<UserActionLogDocument> documents = new ArrayList<>();
            for (Hit<UserActionLogDocument> hit : response.hits().hits()) {
                UserActionLogDocument doc = hit.source();
                if (doc != null) {
                    doc.setId(hit.id());
                    documents.add(doc);
                }
            }

            // 安全地获取总记录数，避免重复调用total()方法
            var totalHits = response.hits().total();
            long total = totalHits != null ? totalHits.value() : 0;
            return new SearchResult(documents, total);

        } catch (Exception e) {
            logger.error("从Elasticsearch搜索用户行为日志失败", e);
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    /**
     * 获取所有操作类型（用于下拉菜单）
     */
    public List<String> getActionTypes() {
        if (!elasticsearchEnabled) {
            logger.warn("Elasticsearch未启用，无法获取操作类型");
            return new ArrayList<>();
        }

        try {
            // 构建terms聚合查询
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0) // 不需要返回具体文档
                .aggregations("action_types", a -> a
                    .terms(t -> t
                        .field("actionType")
                        .size(1000) // 获取所有不同的actionType值
                    )
                )
            );

            if (userLogElasticsearchClient == null) {
                logger.warn("Elasticsearch客户端未初始化");
                return new ArrayList<>();
            }

            SearchResponse<UserActionLogDocument> response = userLogElasticsearchClient.search(
                searchRequest, 
                UserActionLogDocument.class
            );

            // 提取聚合结果
            List<String> actionTypes = new ArrayList<>();
            var aggregations = response.aggregations();
            
            logger.info("Elasticsearch聚合响应: aggregations={}", aggregations);
            
            if (aggregations != null && aggregations.containsKey("action_types")) {
                var termsAggregation = aggregations.get("action_types").sterms();
                logger.info("Terms聚合结果: buckets count={}", termsAggregation != null ? termsAggregation.buckets().array().size() : 0);
                
                if (termsAggregation != null) {
                    for (var bucket : termsAggregation.buckets().array()) {
                        String actionType = bucket.key().stringValue();
                        long count = bucket.docCount();
                        logger.info("发现操作类型: {} (count: {})", actionType, count);
                        actionTypes.add(actionType);
                    }
                }
            } else {
                logger.warn("未找到action_types聚合结果");
            }
            
            logger.info("最终返回的操作类型列表: {}", actionTypes);

            return actionTypes;

        } catch (Exception e) {
            logger.error("从Elasticsearch获取操作类型失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 搜索结果封装类
     */
    public static class SearchResult {
        private final List<UserActionLogDocument> documents;
        private final long total;

        public SearchResult(List<UserActionLogDocument> documents, long total) {
            this.documents = documents;
            this.total = total;
        }

        public List<UserActionLogDocument> getDocuments() {
            return documents;
        }

        public long getTotal() {
            return total;
        }
    }
}
