package com.github.app.dify.analytics.analysis.service.impl;

import com.github.app.dify.analytics.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analytics.analysis.req.GraphQAReq;
import com.github.app.dify.analytics.analysis.req.GraphRAGReq;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analytics.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.analytics.analysis.resp.GraphLinkResp;
import com.github.app.dify.analytics.analysis.resp.GraphNodeResp;
import com.github.app.dify.analytics.analysis.resp.GraphQAResp;
import com.github.app.dify.analytics.analysis.resp.GraphRAGResp;
import com.github.app.dify.analytics.analysis.resp.GraphViewResp;
import com.github.app.dify.analytics.analysis.service.DataAnalysisService;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.KnowledgeBaseDocument;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseDocumentRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.core.TraceSanitizer;
import com.github.app.dify.ops.trace.core.TraceStepCollector;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.system.util.SkillLoader;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class Neo4jSyncService {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jSyncService.class);
    private static final int BATCH_SIZE = 500;

    @Autowired private DatabaseConnectionService databaseConnectionService;
    @Autowired private UserRepository userRepository;
    @Autowired private AiAppRepository aiAppRepository;
    @Autowired private AiAppUserRepository aiAppUserRepository;
    @Autowired private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired private KnowledgeBaseDocumentRepository knowledgeBaseDocumentRepository;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private QAModelRepository qaModelRepository;
    @Transactional(readOnly = true)
    public Map<String, Object> syncToNeo4j(DataSource neo4jDataSource) {
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
                "CREATE CONSTRAINT IF NOT EXISTS FOR (n:QAModel) REQUIRE n.id IS UNIQUE",
                "CREATE INDEX user_username_idx IF NOT EXISTS FOR (n:User) ON (n.username)",
                "CREATE INDEX ai_app_name_idx IF NOT EXISTS FOR (n:AiApp) ON (n.name)",
                "CREATE INDEX knowledge_base_name_idx IF NOT EXISTS FOR (n:KnowledgeBase) ON (n.name)",
                "CREATE INDEX knowledge_document_name_idx IF NOT EXISTS FOR (n:KnowledgeDocument) ON (n.originalFileName)",
                "CREATE INDEX conversation_title_idx IF NOT EXISTS FOR (n:Conversation) ON (n.title)",
                "CREATE INDEX qa_model_name_idx IF NOT EXISTS FOR (n:QAModel) ON (n.name)",
                "CREATE INDEX qa_model_model_idx IF NOT EXISTS FOR (n:QAModel) ON (n.model)"
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

}
