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
import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.service.DataSourceService;
import com.github.app.dify.datasource.service.DatabaseConnectionService;
import com.github.app.dify.system.service.SystemConfigService;
import com.github.app.dify.userlog.document.UserActionLogDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Elasticsearch日志服务
 */
@Service
public class ElasticsearchLogService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchLogService.class);
    private static final String INDEX_NAME = "user_action_logs";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CONFIG_KEY_USERLOG_ELASTICSEARCH_DATASOURCE_ID = "userlog.elasticsearchDataSourceId";
    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    private final Object clientLock = new Object();
    private volatile Long activeDataSourceId;
    private volatile ElasticsearchClient activeClient;
    private volatile boolean indexInitialized;

    /**
     * 初始化：检查并创建索引
     */
    @PostConstruct
    public void init() {
        try {
            ElasticsearchClient client = resolveClient();
            if (client != null) {
                ensureIndexInitialized(client, activeDataSourceId);
            }
        } catch (Exception e) {
            logger.error("初始化Elasticsearch索引失败", e);
        }
    }

    public boolean isEnabled() {
        return resolveClient() != null;
    }

    private ElasticsearchClient resolveClient() {
        String configValue;
        try {
            configValue = systemConfigService.getConfigValue(CONFIG_KEY_USERLOG_ELASTICSEARCH_DATASOURCE_ID);
        } catch (Exception e) {
            logger.warn("读取系统配置失败 - 键: {}", CONFIG_KEY_USERLOG_ELASTICSEARCH_DATASOURCE_ID, e);
            return null;
        }

        Long dataSourceId = parseLongLoose(configValue);
        if (dataSourceId == null) {
            return null;
        }

        ElasticsearchClient cached = activeClient;
        Long cachedId = activeDataSourceId;
        if (cached != null && cachedId != null && cachedId.equals(dataSourceId)) {
            return cached;
        }

        synchronized (clientLock) {
            cached = activeClient;
            cachedId = activeDataSourceId;
            if (cached != null && cachedId != null && cachedId.equals(dataSourceId)) {
                return cached;
            }

            DataSource dataSource;
            try {
                dataSource = dataSourceService.getDataSourceEntityById(dataSourceId);
            } catch (Exception e) {
                logger.warn("用户日志ES数据源不存在或不可用 - 数据源ID: {}", dataSourceId, e);
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
                return null;
            }

            if (dataSource.getStatus() != null && dataSource.getStatus() == 0) {
                logger.warn("用户日志ES数据源已禁用 - 数据源ID: {}", dataSourceId);
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
                return null;
            }

            if (dataSource.getDeleted() != null && dataSource.getDeleted() == 1) {
                logger.warn("用户日志ES数据源已删除 - 数据源ID: {}", dataSourceId);
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
                return null;
            }

            if (dataSource.getType() == null || !"elasticsearch".equalsIgnoreCase(dataSource.getType())) {
                logger.warn("用户日志ES数据源类型不匹配 - 数据源ID: {}, type: {}", dataSourceId, dataSource.getType());
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
                return null;
            }

            ElasticsearchClient client;
            try {
                client = databaseConnectionService.getElasticsearchClient(dataSource);
            } catch (Exception e) {
                logger.error("创建用户日志Elasticsearch客户端失败 - 数据源ID: {}", dataSourceId, e);
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
                return null;
            }

            activeDataSourceId = dataSourceId;
            activeClient = client;
            indexInitialized = false;
            return client;
        }
    }

    private Long parseLongLoose(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }

        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void ensureIndexInitialized(ElasticsearchClient client, Long dataSourceId) {
        if (client == null || dataSourceId == null) {
            return;
        }
        if (indexInitialized && dataSourceId.equals(activeDataSourceId)) {
            return;
        }
        synchronized (clientLock) {
            if (indexInitialized && dataSourceId.equals(activeDataSourceId)) {
                return;
            }
            try {
                ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(INDEX_NAME));
                boolean exists = client.indices().exists(existsRequest).value();
                if (!exists) {
                    logger.info("索引 {} 不存在，开始自动创建...", INDEX_NAME);
                    createIndex(client);
                } else {
                    logger.info("索引 {} 已存在", INDEX_NAME);
                }
                indexInitialized = true;
            } catch (Exception e) {
                logger.error("初始化Elasticsearch索引失败 - 数据源ID: {}", dataSourceId, e);
            }
        }
    }

    /**
     * 创建索引
     */
    private void createIndex(ElasticsearchClient client) {
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
            
            client.indices().create(createIndexRequest);
            logger.info("索引 {} 创建成功", INDEX_NAME);
        } catch (Exception e) {
            logger.error("创建索引失败", e);
        }
    }

    /**
     * 保存日志到Elasticsearch
     */
    public void saveLog(UserActionLogDocument document) {
        ElasticsearchClient client = resolveClient();
        if (client == null) {
            return;
        }

        try {
            ensureIndexInitialized(client, activeDataSourceId);
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

            client.index(request);
            logger.debug("用户行为日志已保存到Elasticsearch: id={}, userId={}, module={}", 
                    document.getId(), document.getUserId(), document.getModule());
        } catch (java.util.concurrent.CancellationException e) {
            logger.error("保存用户行为日志到Elasticsearch失败：请求被取消（可能是超时或连接问题）", e);
            // 清除缓存的客户端，下次重新创建
            synchronized (clientLock) {
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
            }
        } catch (java.lang.RuntimeException e) {
            if (e.getCause() instanceof java.util.concurrent.CancellationException) {
                logger.error("保存用户行为日志到Elasticsearch失败：请求执行被取消（可能是超时或连接问题）", e);
                // 清除缓存的客户端，下次重新创建
                synchronized (clientLock) {
                    activeClient = null;
                    activeDataSourceId = null;
                    indexInitialized = false;
                }
            } else {
                logger.error("保存用户行为日志到Elasticsearch失败", e);
            }
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
        ElasticsearchClient client = resolveClient();
        if (client == null) {
            return new SearchResult(new ArrayList<>(), 0);
        }

        try {
            ensureIndexInitialized(client, activeDataSourceId);
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
            SearchResponse<UserActionLogDocument> response = client.search(
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

        } catch (java.util.concurrent.CancellationException e) {
            logger.error("从Elasticsearch搜索用户行为日志失败：请求被取消（可能是超时或连接问题）", e);
            // 清除缓存的客户端，下次重新创建
            synchronized (clientLock) {
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
            }
            return new SearchResult(new ArrayList<>(), 0);
        } catch (java.lang.RuntimeException e) {
            if (e.getCause() instanceof java.util.concurrent.CancellationException) {
                logger.error("从Elasticsearch搜索用户行为日志失败：请求执行被取消（可能是超时或连接问题）", e);
                // 清除缓存的客户端，下次重新创建
                synchronized (clientLock) {
                    activeClient = null;
                    activeDataSourceId = null;
                    indexInitialized = false;
                }
            } else {
                logger.error("从Elasticsearch搜索用户行为日志失败", e);
            }
            return new SearchResult(new ArrayList<>(), 0);
        } catch (Exception e) {
            logger.error("从Elasticsearch搜索用户行为日志失败", e);
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    /**
     * 获取所有操作类型（用于下拉菜单）
     */
    public List<String> getActionTypes() {
        ElasticsearchClient client = resolveClient();
        if (client == null) {
            return new ArrayList<>();
        }

        try {
            ensureIndexInitialized(client, activeDataSourceId);
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

            SearchResponse<UserActionLogDocument> response = client.search(
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

        } catch (java.util.concurrent.CancellationException e) {
            logger.error("从Elasticsearch获取操作类型失败：请求被取消（可能是超时或连接问题）", e);
            // 清除缓存的客户端，下次重新创建
            synchronized (clientLock) {
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
            }
            return new ArrayList<>();
        } catch (java.lang.RuntimeException e) {
            if (e.getCause() instanceof java.util.concurrent.CancellationException) {
                logger.error("从Elasticsearch获取操作类型失败：请求执行被取消（可能是超时或连接问题）", e);
                // 清除缓存的客户端，下次重新创建
                synchronized (clientLock) {
                    activeClient = null;
                    activeDataSourceId = null;
                    indexInitialized = false;
                }
            } else {
                logger.error("从Elasticsearch获取操作类型失败", e);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("从Elasticsearch获取操作类型失败", e);
            return new ArrayList<>();
        }
    }

    public List<String> getModules() {
        ElasticsearchClient client = resolveClient();
        if (client == null) {
            return new ArrayList<>();
        }
        try {
            ensureIndexInitialized(client, activeDataSourceId);
            SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations("modules", a -> a
                    .terms(t -> t
                        .field("module")
                        .size(1000)
                    )
                )
            );
            SearchResponse<UserActionLogDocument> response = client.search(
                searchRequest,
                UserActionLogDocument.class
            );
            List<String> modules = new ArrayList<>();
            var aggregations = response.aggregations();
            if (aggregations != null && aggregations.containsKey("modules")) {
                var termsAggregation = aggregations.get("modules").sterms();
                if (termsAggregation != null) {
                    for (var bucket : termsAggregation.buckets().array()) {
                        modules.add(bucket.key().stringValue());
                    }
                }
            }
            return modules;
        } catch (java.util.concurrent.CancellationException e) {
            logger.error("从Elasticsearch获取操作模块失败：请求被取消（可能是超时或连接问题）", e);
            // 清除缓存的客户端，下次重新创建
            synchronized (clientLock) {
                activeClient = null;
                activeDataSourceId = null;
                indexInitialized = false;
            }
            return new ArrayList<>();
        } catch (java.lang.RuntimeException e) {
            if (e.getCause() instanceof java.util.concurrent.CancellationException) {
                logger.error("从Elasticsearch获取操作模块失败：请求执行被取消（可能是超时或连接问题）", e);
                // 清除缓存的客户端，下次重新创建
                synchronized (clientLock) {
                    activeClient = null;
                    activeDataSourceId = null;
                    indexInitialized = false;
                }
            } else {
                logger.error("从Elasticsearch获取操作模块失败", e);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            logger.error("从Elasticsearch获取操作模块失败", e);
            return new ArrayList<>();
        }
    }

    /**
         * 搜索结果封装类
         */
        public record SearchResult(List<UserActionLogDocument> documents, long total) {

    }
}
