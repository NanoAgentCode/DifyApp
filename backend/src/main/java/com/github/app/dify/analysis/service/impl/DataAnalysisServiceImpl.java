package com.github.app.dify.analysis.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.analysis.resp.GraphLinkResp;
import com.github.app.dify.analysis.resp.GraphNodeResp;
import com.github.app.dify.analysis.resp.GraphViewResp;
import com.github.app.dify.analysis.service.DataAnalysisService;
import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.AiAppUser;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.chat.repository.AiAppRepository;
import com.github.app.dify.chat.repository.AiAppUserRepository;
import com.github.app.dify.chat.repository.ChatConversationRepository;
import com.github.app.dify.chat.repository.ChatMessageRepository;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.datasource.domain.DataSource;
import com.github.app.dify.datasource.service.DataSourceService;
import com.github.app.dify.datasource.service.DatabaseConnectionService;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.repository.SystemConfigRepository;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DataAnalysisServiceImpl implements DataAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisServiceImpl.class);

    private static final String CONFIG_GROUP = "analysis";

    private static final String KEY_ENABLED = "analysis.etl.enabled";
    private static final String KEY_INTERVAL_MINUTES = "analysis.etl.intervalMinutes";
    private static final String KEY_NEO4J_DATASOURCE_ID = "analysis.neo4j.dataSourceId";

    private static final String KEY_LAST_RUN_AT_MS = "analysis.etl.lastRunAtMs";
    private static final String KEY_LAST_SUCCESS_AT_MS = "analysis.etl.lastSuccessAtMs";
    private static final String KEY_LAST_STATUS = "analysis.etl.lastStatus";
    private static final String KEY_LAST_MESSAGE = "analysis.etl.lastMessage";
    private static final String KEY_LAST_DURATION_MS = "analysis.etl.lastDurationMs";
    private static final String KEY_LAST_METRICS_JSON = "analysis.etl.lastMetricsJson";

    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int BATCH_SIZE = 500;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    private TaskExecutor taskExecutor;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiAppRepository aiAppRepository;

    @Autowired
    private AiAppUserRepository aiAppUserRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private KnowledgeBaseDocumentRepository knowledgeBaseDocumentRepository;

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private QAModelRepository qaModelRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DataAnalysisSettingsResp getSettings() {
        DataAnalysisSettingsResp resp = new DataAnalysisSettingsResp();
        resp.setEnabled(readBoolean(KEY_ENABLED, false));
        resp.setIntervalMinutes(readInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES));
        resp.setNeo4jDataSourceId(readLong(KEY_NEO4J_DATASOURCE_ID, null));
        return resp;
    }

    @Override
    public DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username) {
        Integer intervalMinutes = req.getIntervalMinutes();
        if (intervalMinutes != null && intervalMinutes <= 0) {
            throw new BusinessException("同步间隔必须大于0", ErrorCode.BAD_REQUEST);
        }
        if (intervalMinutes != null && intervalMinutes > 1440) {
            throw new BusinessException("同步间隔不能超过1440分钟", ErrorCode.BAD_REQUEST);
        }

        Long neo4jDataSourceId = req.getNeo4jDataSourceId();
        if (neo4jDataSourceId != null) {
            DataSource dataSource = dataSourceService.getDataSourceEntityById(neo4jDataSourceId);
            if (!"neo4j".equalsIgnoreCase(dataSource.getType())) {
                throw new BusinessException("所选数据源不是Neo4j类型", ErrorCode.BAD_REQUEST);
            }
            if (dataSource.getStatus() != null && dataSource.getStatus() == 0) {
                throw new BusinessException("所选Neo4j数据源已禁用", ErrorCode.BAD_REQUEST);
            }
        }

        writeConfig(KEY_ENABLED, String.valueOf(Boolean.TRUE.equals(req.getEnabled())), "boolean", "是否启用定时同步", userId, username);
        if (intervalMinutes != null) {
            writeConfig(KEY_INTERVAL_MINUTES, String.valueOf(intervalMinutes), "number", "同步间隔（分钟）", userId, username);
        }
        if (neo4jDataSourceId != null) {
            writeConfig(KEY_NEO4J_DATASOURCE_ID, String.valueOf(neo4jDataSourceId), "number", "Neo4j 数据源ID", userId, username);
        } else {
            deleteConfig(KEY_NEO4J_DATASOURCE_ID, userId, username);
        }

        return getSettings();
    }

    @Override
    public DataAnalysisStatusResp getStatus() {
        DataAnalysisStatusResp resp = new DataAnalysisStatusResp();

        DataAnalysisSettingsResp settings = getSettings();
        resp.setEnabled(settings.getEnabled());
        resp.setNeo4jDataSourceId(settings.getNeo4jDataSourceId());
        resp.setIntervalMinutes(settings.getIntervalMinutes());
        resp.setRunning(running.get());

        Long dataSourceId = settings.getNeo4jDataSourceId();
        if (dataSourceId != null) {
            try {
                DataSource ds = dataSourceService.getDataSourceEntityById(dataSourceId);
                resp.setNeo4jDataSourceName(ds.getName());
            } catch (Exception e) {
                resp.setNeo4jDataSourceName(null);
            }
        }

        resp.setLastRunAtMs(readLong(KEY_LAST_RUN_AT_MS, null));
        resp.setLastSuccessAtMs(readLong(KEY_LAST_SUCCESS_AT_MS, null));
        resp.setLastStatus(readString(KEY_LAST_STATUS, "never"));
        resp.setLastMessage(readString(KEY_LAST_MESSAGE, null));
        resp.setLastDurationMs(readLong(KEY_LAST_DURATION_MS, null));

        String metricsJson = readString(KEY_LAST_METRICS_JSON, null);
        if (metricsJson != null && !metricsJson.trim().isEmpty()) {
            try {
                Map<String, Object> metrics = objectMapper.readValue(metricsJson, new TypeReference<Map<String, Object>>() {});
                resp.setMetrics(metrics);
            } catch (Exception e) {
                resp.setMetrics(null);
            }
        }

        return resp;
    }

    @Override
    public void triggerRun(Long userId, String username) {
        DataAnalysisSettingsResp settings = getSettings();
        getValidatedNeo4jDataSourceOrThrow(settings);
        taskExecutor.execute(() -> runInternal(userId, username, true));
    }

    @Override
    public void runIfDue() {
        boolean enabled = readBoolean(KEY_ENABLED, false);
        if (!enabled) {
            return;
        }

        Integer intervalMinutes = readInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES);
        if (intervalMinutes == null || intervalMinutes <= 0) {
            intervalMinutes = DEFAULT_INTERVAL_MINUTES;
        }

        Long lastRunAtMs = readLong(KEY_LAST_RUN_AT_MS, null);
        long now = System.currentTimeMillis();
        if (lastRunAtMs == null) {
            taskExecutor.execute(() -> runInternal(0L, "system", false));
            return;
        }

        long nextAt = lastRunAtMs + intervalMinutes * 60L * 1000L;
        if (now >= nextAt) {
            taskExecutor.execute(() -> runInternal(0L, "system", false));
        }
    }

    @Override
    public GraphViewResp getGraphView(Integer limit) {
        DataAnalysisSettingsResp settings = getSettings();
        int safeLimit = limit == null ? 200 : limit;
        if (safeLimit <= 0) {
            safeLimit = 200;
        }
        if (safeLimit > 2000) {
            safeLimit = 2000;
        }

        DataSource neo4jDataSource = getValidatedNeo4jDataSourceOrThrow(settings);

        Map<String, GraphNodeResp> nodeMap = new LinkedHashMap<>();
        List<GraphLinkResp> links = new ArrayList<>();
        Map<String, Long> nodeCounts = new LinkedHashMap<>();
        Map<String, Long> relationshipCounts = new LinkedHashMap<>();

        try (Session session = databaseConnectionService.getNeo4jSession(neo4jDataSource)) {
            Result nodeCountResult = session.run(
                    "MATCH (n) " +
                            "WITH head(labels(n)) AS label, count(*) AS cnt " +
                            "RETURN coalesce(label, 'Unknown') AS label, cnt " +
                            "ORDER BY cnt DESC"
            );
            for (Record r : nodeCountResult.list()) {
                nodeCounts.put(r.get("label").asString(), r.get("cnt").asLong());
            }

            Result relCountResult = session.run(
                    "MATCH ()-[r]->() " +
                            "RETURN type(r) AS type, count(*) AS cnt " +
                            "ORDER BY cnt DESC"
            );
            for (Record r : relCountResult.list()) {
                relationshipCounts.put(r.get("type").asString(), r.get("cnt").asLong());
            }

            Result result = session.run(
                    "MATCH (a)-[r]->(b) " +
                            "WHERE a.id IS NOT NULL AND b.id IS NOT NULL " +
                            "RETURN head(labels(a)) AS aLabel, toString(a.id) AS aId, " +
                            "coalesce(a.username, a.name, toString(a.id)) AS aName, " +
                            "head(labels(b)) AS bLabel, toString(b.id) AS bId, " +
                            "coalesce(b.username, b.name, toString(b.id)) AS bName, " +
                            "type(r) AS relType " +
                            "LIMIT $limit",
                    Values.parameters("limit", safeLimit)
            );

            for (Record r : result.list()) {
                String aLabel = safeString(r.get("aLabel").asString());
                String aId = safeString(r.get("aId").asString());
                String aName = safeString(r.get("aName").asString());
                String bLabel = safeString(r.get("bLabel").asString());
                String bId = safeString(r.get("bId").asString());
                String bName = safeString(r.get("bName").asString());
                String relType = safeString(r.get("relType").asString());

                String sourceId = aLabel + ":" + aId;
                String targetId = bLabel + ":" + bId;

                nodeMap.computeIfAbsent(sourceId, k -> {
                    GraphNodeResp n = new GraphNodeResp();
                    n.setId(sourceId);
                    n.setLabel(aLabel);
                    n.setName(aName);
                    return n;
                });
                nodeMap.computeIfAbsent(targetId, k -> {
                    GraphNodeResp n = new GraphNodeResp();
                    n.setId(targetId);
                    n.setLabel(bLabel);
                    n.setName(bName);
                    return n;
                });

                GraphLinkResp link = new GraphLinkResp();
                link.setSource(sourceId);
                link.setTarget(targetId);
                link.setType(relType);
                links.add(link);
            }
        }

        GraphViewResp resp = new GraphViewResp();
        resp.setNodes(new ArrayList<>(nodeMap.values()));
        resp.setLinks(links);
        resp.setNodeCounts(nodeCounts);
        resp.setRelationshipCounts(relationshipCounts);
        return resp;
    }

    private DataSource getValidatedNeo4jDataSourceOrThrow(DataAnalysisSettingsResp settings) {
        Long neo4jDataSourceId = settings.getNeo4jDataSourceId();
        if (neo4jDataSourceId == null) {
            throw new BusinessException("未配置Neo4j数据源", ErrorCode.BAD_REQUEST);
        }
        DataSource neo4jDataSource = dataSourceService.getDataSourceEntityById(neo4jDataSourceId);
        if (!"neo4j".equalsIgnoreCase(neo4jDataSource.getType())) {
            throw new BusinessException("配置的数据源不是Neo4j类型", ErrorCode.BAD_REQUEST);
        }
        if (neo4jDataSource.getStatus() != null && neo4jDataSource.getStatus() == 0) {
            throw new BusinessException("配置的Neo4j数据源已禁用", ErrorCode.BAD_REQUEST);
        }
        return neo4jDataSource;
    }

    private void runInternal(Long userId, String username, boolean force) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long start = System.currentTimeMillis();
        writeRuntimeStatus("running", "同步任务开始", start, null, null, userId, username);

        try {
            DataAnalysisSettingsResp settings = getSettings();
            if (!Boolean.TRUE.equals(settings.getEnabled()) && !force) {
                writeRuntimeStatus("never", "同步未启用", null, null, null, userId, username);
                return;
            }

            DataSource neo4jDataSource;
            try {
                neo4jDataSource = getValidatedNeo4jDataSourceOrThrow(settings);
            } catch (BusinessException ex) {
                writeRuntimeStatus("failed", ex.getMessage(), null, null, null, userId, username);
                return;
            }

            Map<String, Object> metrics = syncToNeo4j(neo4jDataSource);

            long duration = System.currentTimeMillis() - start;
            writeRuntimeStatus("success", "同步成功", null, System.currentTimeMillis(), duration, userId, username);
            writeConfig(KEY_LAST_METRICS_JSON, objectMapper.writeValueAsString(metrics), "json", "最近一次同步指标", userId, username);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String message = e.getMessage() != null ? e.getMessage() : "同步失败";
            logger.error("数据同步到Neo4j失败", e);
            writeRuntimeStatus("failed", message, null, null, duration, userId, username);
        } finally {
            running.set(false);
        }
    }

    @Transactional(readOnly = true)
    private Map<String, Object> syncToNeo4j(DataSource neo4jDataSource) {
        // 小表可以一次性加载（通常数据量不大）
        List<User> users = userRepository.findAll();
        List<AiApp> apps = aiAppRepository.findAll();
        List<QAModel> qaModels = qaModelRepository.findAll();
        
        // 构建用户ID映射（用于后续关系处理）
        Map<Long, User> userById = users.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 统计计数器
        final AtomicLong appUsersCount = new AtomicLong(0);
        final AtomicLong knowledgeBasesCount = new AtomicLong(0);
        final AtomicLong documentsCount = new AtomicLong(0);
        final AtomicLong conversationsCount = new AtomicLong(0);
        final AtomicLong messagesCount = new AtomicLong(0);

        try (Session session = databaseConnectionService.getNeo4jSession(neo4jDataSource)) {
            ensureConstraints(session);

            // 处理小表（一次性加载）
            upsertUsers(session, users);
            upsertAiApps(session, apps);
            upsertQAModels(session, qaModels);

            // 处理中等大小的表（使用流式处理）
            processAppUsersInBatches(session, appUsersCount);
            processKnowledgeBasesInBatches(session, knowledgeBasesCount, userById);
            processDocumentsInBatches(session, documentsCount);
            processConversationsInBatches(session, conversationsCount);
            processMessagesInBatches(session, messagesCount);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("users", users.size());
        metrics.put("apps", apps.size());
        metrics.put("appUsers", appUsersCount.get());
        metrics.put("knowledgeBases", knowledgeBasesCount.get());
        metrics.put("documents", documentsCount.get());
        metrics.put("conversations", conversationsCount.get());
        metrics.put("messages", messagesCount.get());
        metrics.put("qaModels", qaModels.size());
        return metrics;
    }

    private void ensureConstraints(Session session) {
        List<String> statements = Arrays.asList(
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:User) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:AiApp) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:KnowledgeBase) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:KnowledgeDocument) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Conversation) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:Message) REQUIRE n.id IS UNIQUE",
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:QAModel) REQUIRE n.id IS UNIQUE"
        );
        for (String cypher : statements) {
            session.executeWrite(tx -> tx.run(cypher).consume());
        }
    }

    private void upsertUsers(Session session, List<User> users) {
        List<Map<String, Object>> rows = users.stream()
                .filter(u -> u.getId() != null)
                .map(u -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", u.getId());
                    row.put("username", safeTrim(u.getUsername()));
                    row.put("role", u.getRole());
                    row.put("status", u.getStatus());
                    row.put("deleted", u.getDeleted());
                    row.put("createTimeMs", toMs(u.getCreateTime()));
                    row.put("updateTimeMs", toMs(u.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (u:User {id: row.id}) " +
                "SET u.username = row.username, u.role = row.role, u.status = row.status, u.deleted = row.deleted, " +
                "u.createTimeMs = row.createTimeMs, u.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertAiApps(Session session, List<AiApp> apps) {
        List<Map<String, Object>> rows = apps.stream()
                .filter(a -> a.getId() != null)
                .map(a -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", a.getId());
                    row.put("name", safeTrim(a.getName()));
                    row.put("description", safeTrim(a.getDescription()));
                    row.put("type", a.getType());
                    row.put("status", a.getStatus());
                    row.put("creator", safeTrim(a.getCreator()));
                    row.put("tenantId", a.getTenantId());
                    row.put("deleted", a.getDeleted());
                    row.put("createTimeMs", toMs(a.getCreateTime()));
                    row.put("updateTimeMs", toMs(a.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (a:AiApp {id: row.id}) " +
                "SET a.name = row.name, a.description = row.description, a.type = row.type, a.status = row.status, " +
                "a.creator = row.creator, a.tenantId = row.tenantId, a.deleted = row.deleted, " +
                "a.createTimeMs = row.createTimeMs, a.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertKnowledgeBases(Session session, List<KnowledgeBase> knowledgeBases) {
        List<Map<String, Object>> rows = knowledgeBases.stream()
                .filter(kb -> kb.getId() != null)
                .map(kb -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", kb.getId());
                    row.put("name", safeTrim(kb.getName()));
                    row.put("description", safeTrim(kb.getDescription()));
                    row.put("status", kb.getStatus());
                    row.put("creator", safeTrim(kb.getCreator()));
                    row.put("creatorId", kb.getCreatorId());
                    row.put("isPublic", kb.getIsPublic());
                    row.put("vectorStoreType", safeTrim(kb.getVectorStoreType()));
                    row.put("vectorDatabaseId", kb.getVectorDatabaseId());
                    row.put("embeddingModelId", kb.getEmbeddingModelId());
                    row.put("topK", kb.getTopK());
                    row.put("tenantId", kb.getTenantId());
                    row.put("summary", kb.getSummary());
                    row.put("deleted", kb.getDeleted());
                    row.put("createTimeMs", toMs(kb.getCreateTime()));
                    row.put("updateTimeMs", toMs(kb.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (kb:KnowledgeBase {id: row.id}) " +
                "SET kb.name = row.name, kb.description = row.description, kb.status = row.status, kb.creator = row.creator, " +
                "kb.creatorId = row.creatorId, kb.isPublic = row.isPublic, kb.vectorStoreType = row.vectorStoreType, " +
                "kb.vectorDatabaseId = row.vectorDatabaseId, kb.embeddingModelId = row.embeddingModelId, kb.topK = row.topK, " +
                "kb.tenantId = row.tenantId, kb.summary = row.summary, kb.deleted = row.deleted, " +
                "kb.createTimeMs = row.createTimeMs, kb.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertKnowledgeBaseDocuments(Session session, List<KnowledgeBaseDocument> documents) {
        List<Map<String, Object>> rows = documents.stream()
                .filter(d -> d.getId() != null)
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("knowledgeBaseId", d.getKnowledgeBaseId());
                    row.put("originalFileName", safeTrim(d.getOriginalFileName()));
                    row.put("fileType", safeTrim(d.getFileType()));
                    row.put("fileSize", d.getFileSize());
                    row.put("mimeType", safeTrim(d.getMimeType()));
                    row.put("status", d.getStatus());
                    row.put("vectorizedStatus", d.getVectorizedStatus());
                    row.put("vectorizedTimeMs", toMs(d.getVectorizedTime()));
                    row.put("vectorizedError", safeTrim(d.getVectorizedError()));
                    row.put("deleted", d.getDeleted());
                    row.put("createTimeMs", toMs(d.getCreateTime()));
                    row.put("updateTimeMs", toMs(d.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (d:KnowledgeDocument {id: row.id}) " +
                "SET d.knowledgeBaseId = row.knowledgeBaseId, d.originalFileName = row.originalFileName, d.fileType = row.fileType, " +
                "d.fileSize = row.fileSize, d.mimeType = row.mimeType, d.status = row.status, d.vectorizedStatus = row.vectorizedStatus, " +
                "d.vectorizedTimeMs = row.vectorizedTimeMs, d.vectorizedError = row.vectorizedError, d.deleted = row.deleted, " +
                "d.createTimeMs = row.createTimeMs, d.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertConversations(Session session, List<ChatConversation> conversations) {
        List<Map<String, Object>> rows = conversations.stream()
                .filter(c -> c.getId() != null)
                .map(c -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", c.getId());
                    row.put("userId", c.getUserId());
                    row.put("appId", c.getAppId());
                    row.put("knowledgeBaseId", c.getKnowledgeBaseId());
                    row.put("type", c.getType());
                    row.put("title", safeTrim(c.getTitle()));
                    row.put("modelId", c.getModelId());
                    row.put("deleted", c.getDeleted());
                    row.put("createTimeMs", toMs(c.getCreateTime()));
                    row.put("updateTimeMs", toMs(c.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (c:Conversation {id: row.id}) " +
                "SET c.userId = row.userId, c.appId = row.appId, c.knowledgeBaseId = row.knowledgeBaseId, c.type = row.type, " +
                "c.title = row.title, c.modelId = row.modelId, c.deleted = row.deleted, c.createTimeMs = row.createTimeMs, c.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertMessages(Session session, List<ChatMessage> messages) {
        List<Map<String, Object>> rows = messages.stream()
                .filter(m -> m.getId() != null)
                .map(m -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", m.getId());
                    row.put("conversationId", m.getConversationId());
                    row.put("role", safeTrim(m.getRole()));
                    row.put("sequence", m.getSequence());
                    row.put("modelId", m.getModelId());
                    row.put("promptTokens", m.getPromptTokens());
                    row.put("completionTokens", m.getCompletionTokens());
                    row.put("totalTokens", m.getTotalTokens());
                    row.put("deleted", m.getDeleted());
                    row.put("createTimeMs", toMs(m.getCreateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (m:Message {id: row.id}) " +
                "SET m.conversationId = row.conversationId, m.role = row.role, m.sequence = row.sequence, m.modelId = row.modelId, " +
                "m.promptTokens = row.promptTokens, m.completionTokens = row.completionTokens, m.totalTokens = row.totalTokens, " +
                "m.deleted = row.deleted, m.createTimeMs = row.createTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertQAModels(Session session, List<QAModel> qaModels) {
        List<Map<String, Object>> rows = qaModels.stream()
                .filter(m -> m.getId() != null)
                .map(m -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", m.getId());
                    row.put("name", safeTrim(m.getName()));
                    row.put("provider", safeTrim(m.getProvider()));
                    row.put("providerType", safeTrim(m.getProviderType()));
                    row.put("apiUrl", safeTrim(m.getApiUrl()));
                    row.put("model", safeTrim(m.getModel()));
                    row.put("useFor", safeTrim(m.getUseFor()));
                    row.put("enabled", m.getEnabled());
                    row.put("isDefault", m.getIsDefault());
                    row.put("supportsMultimodal", m.getSupportsMultimodal());
                    row.put("supportsVision", m.getSupportsVision());
                    row.put("deleted", m.getDeleted());
                    row.put("createTimeMs", toMs(m.getCreateTime()));
                    row.put("updateTimeMs", toMs(m.getUpdateTime()));
                    return row;
                })
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MERGE (m:QAModel {id: row.id}) " +
                "SET m.name = row.name, m.provider = row.provider, m.providerType = row.providerType, m.apiUrl = row.apiUrl, " +
                "m.model = row.model, m.useFor = row.useFor, m.enabled = row.enabled, m.isDefault = row.isDefault, " +
                "m.supportsMultimodal = row.supportsMultimodal, m.supportsVision = row.supportsVision, m.deleted = row.deleted, " +
                "m.createTimeMs = row.createTimeMs, m.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertAppUserRelations(Session session, List<AiAppUser> appUsers) {
        List<Map<String, Object>> rows = appUsers.stream()
                .map(au -> {
                    Long userId = tryParseLong(au.getUserId());
                    if (userId == null || au.getAppId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", userId);
                    row.put("appId", au.getAppId());
                    row.put("userName", safeTrim(au.getUserName()));
                    row.put("roleType", au.getRoleType());
                    row.put("status", au.getStatus());
                    row.put("deleted", au.getDeleted());
                    row.put("createTimeMs", toMs(au.getCreateTime()));
                    row.put("updateTimeMs", toMs(au.getUpdateTime()));
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MATCH (u:User {id: row.userId}) " +
                "MATCH (a:AiApp {id: row.appId}) " +
                "MERGE (u)-[r:HAS_APP]->(a) " +
                "SET r.userName = row.userName, r.roleType = row.roleType, r.status = row.status, r.deleted = row.deleted, " +
                "r.createTimeMs = row.createTimeMs, r.updateTimeMs = row.updateTimeMs";
        writeBatches(session, cypher, rows);
    }

    private void upsertKnowledgeBaseCreatorRelations(Session session, List<KnowledgeBase> knowledgeBases, Map<Long, User> userById) {
        List<Map<String, Object>> rows = knowledgeBases.stream()
                .map(kb -> {
                    if (kb.getId() == null || kb.getCreatorId() == null) {
                        return null;
                    }
                    if (!userById.containsKey(kb.getCreatorId())) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", kb.getCreatorId());
                    row.put("kbId", kb.getId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MATCH (u:User {id: row.userId}) " +
                "MATCH (kb:KnowledgeBase {id: row.kbId}) " +
                "MERGE (u)-[:CREATED_KB]->(kb)";
        writeBatches(session, cypher, rows);
    }

    private void upsertDocumentRelations(Session session, List<KnowledgeBaseDocument> documents) {
        List<Map<String, Object>> rows = documents.stream()
                .map(d -> {
                    if (d.getId() == null || d.getKnowledgeBaseId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("docId", d.getId());
                    row.put("kbId", d.getKnowledgeBaseId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MATCH (kb:KnowledgeBase {id: row.kbId}) " +
                "MATCH (d:KnowledgeDocument {id: row.docId}) " +
                "MERGE (kb)-[:HAS_DOCUMENT]->(d)";
        writeBatches(session, cypher, rows);
    }

    private void upsertConversationRelations(Session session, List<ChatConversation> conversations) {
        List<Map<String, Object>> userConvRows = conversations.stream()
                .map(c -> {
                    if (c.getId() == null || c.getUserId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("userId", c.getUserId());
                    row.put("convId", c.getId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String userConvCypher = "UNWIND $rows AS row " +
                "MATCH (u:User {id: row.userId}) " +
                "MATCH (c:Conversation {id: row.convId}) " +
                "MERGE (u)-[:HAS_CONVERSATION]->(c)";
        writeBatches(session, userConvCypher, userConvRows);

        List<Map<String, Object>> convAppRows = conversations.stream()
                .map(c -> {
                    if (c.getId() == null || c.getAppId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("convId", c.getId());
                    row.put("appId", c.getAppId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String convAppCypher = "UNWIND $rows AS row " +
                "MATCH (c:Conversation {id: row.convId}) " +
                "MATCH (a:AiApp {id: row.appId}) " +
                "MERGE (c)-[:USING_APP]->(a)";
        writeBatches(session, convAppCypher, convAppRows);

        List<Map<String, Object>> convKbRows = conversations.stream()
                .map(c -> {
                    if (c.getId() == null || c.getKnowledgeBaseId() == null) {
                        return null;
                    }
                    if (c.getType() != null && c.getType() == 3) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("convId", c.getId());
                    row.put("kbId", c.getKnowledgeBaseId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String convKbCypher = "UNWIND $rows AS row " +
                "MATCH (c:Conversation {id: row.convId}) " +
                "MATCH (kb:KnowledgeBase {id: row.kbId}) " +
                "MERGE (c)-[:USING_KB]->(kb)";
        writeBatches(session, convKbCypher, convKbRows);
    }

    private void upsertMessageRelations(Session session, List<ChatMessage> messages) {
        List<Map<String, Object>> rows = messages.stream()
                .map(m -> {
                    if (m.getId() == null || m.getConversationId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("messageId", m.getId());
                    row.put("convId", m.getConversationId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MATCH (c:Conversation {id: row.convId}) " +
                "MATCH (m:Message {id: row.messageId}) " +
                "MERGE (c)-[:HAS_MESSAGE]->(m)";
        writeBatches(session, cypher, rows);
    }

    private void upsertMessageModelRelations(Session session, List<ChatMessage> messages) {
        List<Map<String, Object>> rows = messages.stream()
                .map(m -> {
                    if (m.getId() == null || m.getModelId() == null) {
                        return null;
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("messageId", m.getId());
                    row.put("modelId", m.getModelId());
                    return row;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String cypher = "UNWIND $rows AS row " +
                "MATCH (m:Message {id: row.messageId}) " +
                "MATCH (qm:QAModel {id: row.modelId}) " +
                "MERGE (m)-[:USING_MODEL]->(qm)";
        writeBatches(session, cypher, rows);
    }

    private void writeBatches(Session session, String cypher, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);
            session.executeWrite(tx -> tx.run(cypher, Values.parameters("rows", batch)).consume());
        }
    }

    /**
     * 分批处理 AiAppUser（使用流式查询避免全表加载）
     */
    private void processAppUsersInBatches(Session session, AtomicLong counter) {
        logger.info("开始分批处理 AiAppUser 数据");
        int page = 0;
        Pageable pageable = PageRequest.of(page, BATCH_SIZE);
        List<AiAppUser> batch;
        
        do {
            batch = aiAppUserRepository.findAll(pageable).getContent();
            if (!batch.isEmpty()) {
                upsertAppUserRelations(session, batch);
                counter.addAndGet(batch.size());
                logger.debug("已处理 {} 条 AiAppUser 记录，累计: {}", batch.size(), counter.get());
            }
            page++;
            pageable = PageRequest.of(page, BATCH_SIZE);
        } while (!batch.isEmpty());
        
        logger.info("AiAppUser 分批处理完成，总计: {}", counter.get());
    }

    /**
     * 分批处理 KnowledgeBase（使用流式查询避免全表加载）
     */
    private void processKnowledgeBasesInBatches(Session session, AtomicLong counter, Map<Long, User> userById) {
        logger.info("开始分批处理 KnowledgeBase 数据");
        int page = 0;
        Pageable pageable = PageRequest.of(page, BATCH_SIZE);
        List<KnowledgeBase> batch;
        
        do {
            batch = knowledgeBaseRepository.findAll(pageable).getContent();
            if (!batch.isEmpty()) {
                upsertKnowledgeBases(session, batch);
                // 立即处理关系，避免累积所有数据
                upsertKnowledgeBaseCreatorRelations(session, batch, userById);
                counter.addAndGet(batch.size());
                logger.debug("已处理 {} 条 KnowledgeBase 记录，累计: {}", batch.size(), counter.get());
            }
            page++;
            pageable = PageRequest.of(page, BATCH_SIZE);
        } while (!batch.isEmpty());
        
        logger.info("KnowledgeBase 分批处理完成，总计: {}", counter.get());
    }

    /**
     * 分批处理 KnowledgeBaseDocument（使用流式查询避免全表加载）
     */
    private void processDocumentsInBatches(Session session, AtomicLong counter) {
        logger.info("开始分批处理 KnowledgeBaseDocument 数据");
        int page = 0;
        Pageable pageable = PageRequest.of(page, BATCH_SIZE);
        List<KnowledgeBaseDocument> batch;
        
        do {
            batch = knowledgeBaseDocumentRepository.findAll(pageable).getContent();
            if (!batch.isEmpty()) {
                upsertKnowledgeBaseDocuments(session, batch);
                // 立即处理关系，避免累积所有数据
                upsertDocumentRelations(session, batch);
                counter.addAndGet(batch.size());
                logger.debug("已处理 {} 条 KnowledgeBaseDocument 记录，累计: {}", batch.size(), counter.get());
            }
            page++;
            pageable = PageRequest.of(page, BATCH_SIZE);
        } while (!batch.isEmpty());
        
        logger.info("KnowledgeBaseDocument 分批处理完成，总计: {}", counter.get());
    }

    /**
     * 分批处理 ChatConversation（使用流式查询避免全表加载）
     */
    private void processConversationsInBatches(Session session, AtomicLong counter) {
        logger.info("开始分批处理 ChatConversation 数据");
        int page = 0;
        Pageable pageable = PageRequest.of(page, BATCH_SIZE);
        List<ChatConversation> batch;
        
        do {
            batch = chatConversationRepository.findAll(pageable).getContent();
            if (!batch.isEmpty()) {
                upsertConversations(session, batch);
                // 立即处理关系，避免累积所有数据
                upsertConversationRelations(session, batch);
                counter.addAndGet(batch.size());
                logger.debug("已处理 {} 条 ChatConversation 记录，累计: {}", batch.size(), counter.get());
            }
            page++;
            pageable = PageRequest.of(page, BATCH_SIZE);
        } while (!batch.isEmpty());
        
        logger.info("ChatConversation 分批处理完成，总计: {}", counter.get());
    }

    /**
     * 分批处理 ChatMessage（使用流式查询避免全表加载）
     * 这是最关键的方法，因为 ChatMessage 表可能包含数百万条记录
     */
    private void processMessagesInBatches(Session session, AtomicLong counter) {
        logger.info("开始分批处理 ChatMessage 数据（使用流式查询避免内存溢出）");
        int page = 0;
        Pageable pageable = PageRequest.of(page, BATCH_SIZE);
        List<ChatMessage> batch;
        
        do {
            batch = chatMessageRepository.findAll(pageable).getContent();
            if (!batch.isEmpty()) {
                upsertMessages(session, batch);
                // 立即处理关系，避免累积所有数据
                upsertMessageRelations(session, batch);
                upsertMessageModelRelations(session, batch);
                counter.addAndGet(batch.size());
                if (page % 10 == 0) { // 每10页记录一次日志，避免日志过多
                    logger.info("已处理 {} 页 ChatMessage 数据，累计: {} 条", page + 1, counter.get());
                }
            }
            page++;
            pageable = PageRequest.of(page, BATCH_SIZE);
        } while (!batch.isEmpty());
        
        logger.info("ChatMessage 分批处理完成，总计: {}", counter.get());
    }

    private String safeTrim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String safeString(String s) {
        if (s == null) {
            return "";
        }
        return s;
    }

    private Long toMs(Date date) {
        return date == null ? null : date.getTime();
    }

    private Long tryParseLong(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(t);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeRuntimeStatus(String status, String message, Long lastRunAtMs, Long lastSuccessAtMs, Long durationMs, Long userId, String username) {
        if (lastRunAtMs != null) {
            writeConfig(KEY_LAST_RUN_AT_MS, String.valueOf(lastRunAtMs), "number", "最近一次开始同步时间（毫秒）", userId, username);
        }
        if (lastSuccessAtMs != null) {
            writeConfig(KEY_LAST_SUCCESS_AT_MS, String.valueOf(lastSuccessAtMs), "number", "最近一次同步成功时间（毫秒）", userId, username);
        }
        if (durationMs != null) {
            writeConfig(KEY_LAST_DURATION_MS, String.valueOf(durationMs), "number", "最近一次同步耗时（毫秒）", userId, username);
        }
        if (status != null) {
            writeConfig(KEY_LAST_STATUS, status, "string", "最近一次同步状态", userId, username);
        }
        if (message != null) {
            writeConfig(KEY_LAST_MESSAGE, message, "string", "最近一次同步消息", userId, username);
        }
    }

    private void writeConfig(String key, String value, String type, String description, Long userId, String username) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(key);
        SystemConfig config;
        if (optional.isPresent()) {
            config = optional.get();
        } else {
            Optional<SystemConfig> deletedOptional = systemConfigRepository.findByConfigKey(key);
            if (deletedOptional.isPresent()) {
                config = deletedOptional.get();
                config.setDeleted(0);
            } else {
                config = new SystemConfig();
                config.setConfigKey(key);
                config.setDeleted(0);
                config.setCreator(username);
                config.setCreatorId(userId);
                config.setCreateTime(new Date());
            }
        }

        config.setConfigValue(value);
        config.setConfigGroup(CONFIG_GROUP);
        config.setConfigType(type);
            config.setDescription(description);
        config.setUpdateTime(new Date());
        systemConfigRepository.save(config);
    }

    private void deleteConfig(String key, Long userId, String username) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(key);
        if (!optional.isPresent()) {
            return;
        }
        SystemConfig config = optional.get();
        config.setDeleted(1);
        config.setUpdateTime(new Date());
        systemConfigRepository.save(config);
    }

    private String readString(String key, String defaultValue) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(key);
        return optional.map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    private Boolean readBoolean(String key, boolean defaultValue) {
        String v = readString(key, null);
        if (v == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(v.trim());
    }

    private Integer readInt(String key, Integer defaultValue) {
        String v = readString(key, null);
        if (v == null || v.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long readLong(String key, Long defaultValue) {
        String v = readString(key, null);
        if (v == null || v.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}

