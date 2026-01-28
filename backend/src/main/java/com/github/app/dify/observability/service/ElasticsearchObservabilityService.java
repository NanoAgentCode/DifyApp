package com.github.app.dify.observability.service;

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
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.service.DataSourceService;
import com.github.app.dify.datasource.service.DatabaseConnectionService;
import com.github.app.dify.observability.document.LLMTraceDocument;
import com.github.app.dify.system.service.SystemConfigService;
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
import java.util.stream.Collectors;

/**
 * Elasticsearch Observability服务
 * 
 * 简化设计：
 * 1. 保存时生成 UUID 作为 ES 文档 ID
 * 2. 查询时使用 ES 的 _id 作为唯一标识
 * 3. 所有操作都基于 ES 文档 ID
 */
@Service
public class ElasticsearchObservabilityService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchObservabilityService.class);
    private static final String INDEX_NAME = "llm_traces";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final String CONFIG_KEY = "observability.elasticsearchDataSourceId";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

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

    @PostConstruct
    public void init() {
        try {
            ElasticsearchClient client = getClient();
            if (client != null) {
                ensureIndexExists(client);
            }
        } catch (Exception e) {
            logger.error("初始化Elasticsearch索引失败", e);
        }
    }

    public boolean isEnabled() {
        return getClient() != null;
    }

    /**
     * 获取 ES 客户端
     */
    private ElasticsearchClient getClient() {
        String configValue;
        try {
            configValue = systemConfigService.getConfigValue(CONFIG_KEY);
        } catch (Exception e) {
            logger.warn("读取ES配置失败", e);
            return null;
        }

        Long dataSourceId = parseDataSourceId(configValue);
        if (dataSourceId == null) {
            return null;
        }

        // 检查缓存
        if (activeClient != null && dataSourceId.equals(activeDataSourceId)) {
            return activeClient;
        }

        synchronized (clientLock) {
            if (activeClient != null && dataSourceId.equals(activeDataSourceId)) {
                return activeClient;
            }

            try {
                DataSource ds = dataSourceService.getDataSourceEntityById(dataSourceId);
                if (ds == null || !"elasticsearch".equalsIgnoreCase(ds.getType())) {
                    logger.warn("数据源不存在或类型不是Elasticsearch: id={}", dataSourceId);
                    return null;
                }

                ElasticsearchClient client = databaseConnectionService.getElasticsearchClient(ds);
                if (client != null) {
                    activeClient = client;
                    activeDataSourceId = dataSourceId;
                    indexInitialized = false;
                    ensureIndexExists(client);
                    logger.info("ES客户端已连接: dataSourceId={}", dataSourceId);
                }
                return client;
            } catch (Exception e) {
                logger.error("获取ES客户端失败: dataSourceId={}", dataSourceId, e);
                return null;
            }
        }
    }

    private Long parseDataSourceId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            Matcher m = NUMBER_PATTERN.matcher(value);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
            return null;
        }
    }

    private void ensureIndexExists(ElasticsearchClient client) {
        if (indexInitialized) return;

        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(INDEX_NAME))).value();
            if (!exists) {
                createIndex(client);
            }
            indexInitialized = true;
        } catch (Exception e) {
            logger.error("检查/创建索引失败", e);
        }
    }

    private void createIndex(ElasticsearchClient client) throws Exception {
        TypeMapping mapping = TypeMapping.of(m -> m
            .properties("traceId", Property.of(p -> p.keyword(k -> k)))
            .properties("conversationId", Property.of(p -> p.keyword(k -> k)))
            .properties("appName", Property.of(p -> p.keyword(k -> k)))
            .properties("model", Property.of(p -> p.keyword(k -> k)))
            .properties("provider", Property.of(p -> p.keyword(k -> k)))
            .properties("traceSource", Property.of(p -> p.keyword(k -> k)))
            .properties("status", Property.of(p -> p.integer(i -> i)))
            .properties("inputTokens", Property.of(p -> p.integer(i -> i)))
            .properties("outputTokens", Property.of(p -> p.integer(i -> i)))
            .properties("totalTokens", Property.of(p -> p.integer(i -> i)))
            .properties("latency", Property.of(p -> p.long_(l -> l)))
            .properties("requestContent", Property.of(p -> p.text(t -> t)))
            .properties("responseContent", Property.of(p -> p.text(t -> t)))
            .properties("errorContent", Property.of(p -> p.text(t -> t)))
            .properties("metaData", Property.of(p -> p.text(t -> t)))
            .properties("createdAt", Property.of(p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss"))))
            .properties("finishedAt", Property.of(p -> p.date(d -> d.format("yyyy-MM-dd'T'HH:mm:ss"))))
        );

        IndexSettings settings = IndexSettings.of(s -> s
            .numberOfShards("1")
            .numberOfReplicas("0")
        );

        client.indices().create(CreateIndexRequest.of(c -> c
            .index(INDEX_NAME)
            .mappings(mapping)
            .settings(settings)
        ));
        logger.info("索引已创建: {}", INDEX_NAME);
    }

    // ==================== CRUD 操作 ====================

    /**
     * 保存追踪文档
     * @return 生成的 ES 文档 ID
     */
    public String save(LLMTraceDocument doc) {
        ElasticsearchClient client = getClient();
        if (client == null) {
            logger.warn("ES客户端不可用，无法保存");
            return null;
        }

        try {
            ensureIndexExists(client);

            // 生成 UUID 作为文档 ID
            String docId = UUID.randomUUID().toString();

            IndexRequest<LLMTraceDocument> request = IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id(docId)
                .document(doc)
            );

            client.index(request);
            logger.info("追踪已保存: docId={}, traceId={}", docId, doc.getTraceId());
            return docId;
        } catch (Exception e) {
            logger.error("保存追踪失败", e);
            resetClient();
            return null;
        }
    }

    /**
     * 根据文档 ID 查询
     */
    public LLMTraceDocument getById(String docId) {
        if (docId == null || docId.isEmpty()) {
            return null;
        }

        ElasticsearchClient client = getClient();
        if (client == null) {
            logger.warn("ES客户端不可用");
            return null;
        }

        try {
            ensureIndexExists(client);

            var response = client.get(g -> g.index(INDEX_NAME).id(docId), LLMTraceDocument.class);

            if (response.found() && response.source() != null) {
                LLMTraceDocument doc = response.source();
                doc.setId(docId);
                logger.debug("查询成功: docId={}", docId);
                return doc;
            }

            logger.debug("文档不存在: docId={}", docId);
            return null;
        } catch (Exception e) {
            logger.error("查询失败: docId={}", docId, e);
            resetClient();
            return null;
        }
    }

    /**
     * 根据 traceId 字段查询最新的文档
     */
    public LLMTraceDocument getByTraceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return null;
        }

        ElasticsearchClient client = getClient();
        if (client == null) {
            return null;
        }

        try {
            ensureIndexExists(client);

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(q -> q.term(t -> t.field("traceId").value(traceId)))
                .size(1)
                .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );

            SearchResponse<LLMTraceDocument> response = client.search(request, LLMTraceDocument.class);

            if (!response.hits().hits().isEmpty()) {
                Hit<LLMTraceDocument> hit = response.hits().hits().get(0);
                LLMTraceDocument doc = hit.source();
                if (doc != null) {
                    doc.setId(hit.id());
                    return doc;
                }
            }
            return null;
        } catch (Exception e) {
            logger.error("根据traceId查询失败: traceId={}", traceId, e);
            resetClient();
            return null;
        }
    }

    /**
     * 更新文档
     */
    public void update(String docId, LLMTraceDocument doc) {
        if (docId == null || docId.isEmpty()) {
            logger.warn("无效的文档ID");
            return;
        }

        ElasticsearchClient client = getClient();
        if (client == null) {
            return;
        }

        try {
            ensureIndexExists(client);

            IndexRequest<LLMTraceDocument> request = IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id(docId)
                .document(doc)
            );

            client.index(request);
            logger.info("追踪已更新: docId={}", docId);
        } catch (Exception e) {
            logger.error("更新失败: docId={}", docId, e);
            resetClient();
        }
    }

    /**
     * 删除文档
     */
    public void delete(String docId) {
        if (docId == null || docId.isEmpty()) {
            throw new NotFoundException("无效的文档ID");
        }

        ElasticsearchClient client = getClient();
        if (client == null) {
            throw new NotFoundException("ES客户端不可用");
        }

        try {
            ensureIndexExists(client);

            var response = client.delete(d -> d.index(INDEX_NAME).id(docId));

            if (response.result() == co.elastic.clients.elasticsearch._types.Result.NotFound) {
                throw new NotFoundException("文档不存在: " + docId);
            }

            logger.info("追踪已删除: docId={}", docId);
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("删除失败: docId={}", docId, e);
            resetClient();
            throw new RuntimeException("删除失败: " + e.getMessage(), e);
        }
    }

    // ==================== 搜索操作 ====================

    /**
     * 搜索追踪
     */
    public SearchResult search(String model, String provider, String traceSource,
                               String conversationId, LocalDateTime startTime, LocalDateTime endTime,
                               int page, int pageSize) {
        ElasticsearchClient client = getClient();
        if (client == null) {
            return new SearchResult(new ArrayList<>(), 0);
        }

        try {
            ensureIndexExists(client);

            // 构建查询条件
            List<Query> mustQueries = new ArrayList<>();

            if (model != null && !model.isEmpty()) {
                mustQueries.add(Query.of(q -> q.wildcard(w -> w.field("model").value("*" + model + "*"))));
            }
            if (provider != null && !provider.isEmpty()) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("provider").value(provider))));
            }
            if (traceSource != null && !traceSource.isEmpty()) {
                mustQueries.add(Query.of(q -> q.term(t -> t.field("traceSource").value(traceSource))));
            }
            if (conversationId != null && !conversationId.isEmpty()) {
                mustQueries.add(Query.of(q -> q.wildcard(w -> w.field("conversationId").value("*" + conversationId + "*"))));
            }
            if (startTime != null || endTime != null) {
                final LocalDateTime finalStart = startTime;
                final LocalDateTime finalEnd = endTime;
                mustQueries.add(Query.of(q -> q.range(r -> {
                    var builder = r.field("createdAt");
                    if (finalStart != null) {
                        builder.gte(co.elastic.clients.json.JsonData.of(finalStart.format(FORMATTER)));
                    }
                    if (finalEnd != null) {
                        builder.lte(co.elastic.clients.json.JsonData.of(finalEnd.format(FORMATTER)));
                    }
                    return builder;
                })));
            }

            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            if (!mustQueries.isEmpty()) {
                boolBuilder.must(mustQueries);
            }

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .query(q -> q.bool(boolBuilder.build()))
                .from((page - 1) * pageSize)
                .size(pageSize)
                .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
            );

            SearchResponse<LLMTraceDocument> response = client.search(request, LLMTraceDocument.class);

            List<LLMTraceDocument> docs = new ArrayList<>();
            for (Hit<LLMTraceDocument> hit : response.hits().hits()) {
                LLMTraceDocument doc = hit.source();
                if (doc != null) {
                    doc.setId(hit.id());  // 关键：设置 ES 文档 ID
                    docs.add(doc);
                }
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return new SearchResult(docs, total);

        } catch (Exception e) {
            logger.error("搜索失败", e);
            resetClient();
            return new SearchResult(new ArrayList<>(), 0);
        }
    }

    // ==================== 聚合查询 ====================

    public List<String> getModels() {
        return getAggregation("model");
    }

    public List<String> getProviders() {
        return getAggregation("provider");
    }

    public List<String> getTraceSources() {
        return getAggregation("traceSource");
    }

    private List<String> getAggregation(String field) {
        ElasticsearchClient client = getClient();
        if (client == null) {
            return new ArrayList<>();
        }

        try {
            ensureIndexExists(client);

            SearchRequest request = SearchRequest.of(s -> s
                .index(INDEX_NAME)
                .size(0)
                .aggregations(field + "s", a -> a.terms(t -> t.field(field).size(1000)))
            );

            SearchResponse<LLMTraceDocument> response = client.search(request, LLMTraceDocument.class);

            List<String> values = new ArrayList<>();
            var aggs = response.aggregations();
            if (aggs != null && aggs.containsKey(field + "s")) {
                var terms = aggs.get(field + "s").sterms();
                if (terms != null) {
                    for (var bucket : terms.buckets().array()) {
                        String value = bucket.key().stringValue();
                        if (value != null && !value.trim().isEmpty()) {
                            values.add(value);
                        }
                    }
                }
            }

            return values.stream().distinct().sorted().collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("聚合查询失败: field={}", field, e);
            resetClient();
            return new ArrayList<>();
        }
    }

    private void resetClient() {
        synchronized (clientLock) {
            activeClient = null;
            activeDataSourceId = null;
            indexInitialized = false;
        }
    }

    /**
     * 搜索结果
     */
    public record SearchResult(List<LLMTraceDocument> documents, long total) {}
}
