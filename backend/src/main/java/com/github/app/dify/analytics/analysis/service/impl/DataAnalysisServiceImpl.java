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
import com.github.app.dify.datasource.service.DatabaseConnectionService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DataAnalysisServiceImpl implements DataAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(DataAnalysisServiceImpl.class);

    private static final int BATCH_SIZE = 500;
    private static final long GRAPH_QUERY_SLOW_MS = 1500L;
    private static final long GRAPH_RAG_SLOW_MS = 5000L;
    private static final int GRAPH_RAG_MAX_QUESTION_LENGTH = 500;
    private static final int GRAPH_RAG_MAX_PROMPT_SOURCES = 50;
    private static final int GRAPH_RAG_MAX_LIMIT = 80;
    private static final Set<String> GRAPH_LABELS = new LinkedHashSet<>(Arrays.asList(
            "User", "AiApp", "KnowledgeBase", "KnowledgeDocument", "Conversation", "Message", "QAModel"
    ));
    private static final Set<String> GRAPH_RELATIONSHIP_TYPES = new LinkedHashSet<>(Arrays.asList(
            "HAS_APP", "CREATED_KB", "HAS_DOCUMENT", "HAS_CONVERSATION", "USING_APP", "USING_KB", "HAS_MESSAGE", "USING_MODEL"
    ));
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[G(\\d+)]");
    private static final Set<String> GRAPH_RAG_STOP_WORDS = new HashSet<>(Arrays.asList(
            "哪些", "哪个", "什么", "怎么", "如何", "是否", "有没有", "有多少", "多少", "最近", "相关", "关联",
            "分别", "以及", "和", "与", "的", "了", "吗", "呢", "请", "帮我", "一下", "列出", "查看", "查询",
            "用户", "知识库", "文档", "文件", "会话", "对话", "消息", "模型", "应用", "图谱", "关系", "节点",
            "user", "kb", "app", "model", "graph", "rag"
    ));

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private DataAnalysisConfigService dataAnalysisConfigService;

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

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired
    private TraceFacade traceFacade;

    @Autowired
    private TraceSanitizer traceSanitizer;

    @Override
    public DataAnalysisSettingsResp getSettings() {
        return dataAnalysisConfigService.getSettings();
    }

    @Override
    public DataAnalysisSettingsResp updateSettings(DataAnalysisSettingsReq req, Long userId, String username) {
        return dataAnalysisConfigService.updateSettings(req, userId, username);
    }

    @Override
    public DataAnalysisStatusResp getStatus() {
        return dataAnalysisConfigService.getStatus(running.get());
    }
    @Override
    public void triggerRun(Long userId, String username) {
        DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
        dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
        taskExecutor.execute(() -> runInternal(userId, username, true));
    }

    @Override
    public void runIfDue() {
        boolean enabled = dataAnalysisConfigService.readBoolean();
        if (!enabled) {
            return;
        }

        Integer intervalMinutes = dataAnalysisConfigService.readIntervalMinutes();
        if (intervalMinutes == null || intervalMinutes <= 0) {
            intervalMinutes = DataAnalysisConfigService.DEFAULT_INTERVAL_MINUTES;
        }

        Long lastRunAtMs = dataAnalysisConfigService.readLong(DataAnalysisConfigService.KEY_LAST_RUN_AT_MS, null);
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
    public GraphViewResp getGraphView(Integer limit, String keyword, String nodeLabel, String relationshipType, Integer depth) {
        DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
        int safeLimit = sanitizeLimit(limit, 200, 2000);
        int safeDepth = sanitizeDepth(depth);
        String safeKeyword = safeTrim(keyword);
        String safeNodeLabel = normalizeGraphLabel(nodeLabel);
        String safeRelationshipType = normalizeRelationshipType(relationshipType);

        DataSource neo4jDataSource = dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);

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

            long graphQueryStart = System.currentTimeMillis();
            Result result = session.run(buildGraphViewCypher(safeDepth),
                    Values.parameters(
                            "limit", safeLimit,
                            "keyword", safeKeyword,
                            "nodeLabel", safeNodeLabel,
                            "relationshipType", safeRelationshipType
                    ));
            List<Record> graphRecords = result.list();
            long graphQueryMs = System.currentTimeMillis() - graphQueryStart;
            warnIfSlow("图谱视图查询", graphQueryMs,
                    "limit=" + safeLimit + ",depth=" + safeDepth + ",keyword=" + safeKeyword
                            + ",nodeLabel=" + safeNodeLabel + ",relationshipType=" + safeRelationshipType);

            for (Record r : graphRecords) {
                String aLabel = safeString(r.get("aLabel").asString());
                String aId = safeString(r.get("aId").asString());
                String aName = safeString(r.get("aName").asString());
                String bLabel = safeString(r.get("bLabel").asString());
                String bId = safeString(r.get("bId").asString());
                String bName = safeString(r.get("bName").asString());
                String relType = safeString(r.get("relType").asString());
                Map<String, Object> aProps = r.get("aProps").asMap();
                Map<String, Object> bProps = r.get("bProps").asMap();

                String sourceId = aLabel + ":" + aId;
                String targetId = bLabel + ":" + bId;

                nodeMap.computeIfAbsent(sourceId, k -> buildGraphNode(sourceId, aLabel, aName, aProps));
                nodeMap.computeIfAbsent(targetId, k -> buildGraphNode(targetId, bLabel, bName, bProps));

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

    private String buildGraphViewCypher(int depth) {
        String nodeNameExpr = "coalesce(n.username, n.name, n.title, n.originalFileName, n.model, toString(n.id))";
        String endpointNameExprA = "coalesce(a.username, a.name, a.title, a.originalFileName, a.model, toString(a.id))";
        String endpointNameExprB = "coalesce(b.username, b.name, b.title, b.originalFileName, b.model, toString(b.id))";

        if (depth <= 1) {
            return "MATCH (a)-[r]->(b) " +
                    "WHERE a.id IS NOT NULL AND b.id IS NOT NULL " +
                    "AND ($nodeLabel IS NULL OR $nodeLabel IN labels(a) OR $nodeLabel IN labels(b)) " +
                    "AND ($relationshipType IS NULL OR type(r) = $relationshipType) " +
                    "AND ($keyword IS NULL OR " +
                    "toLower(" + endpointNameExprA + ") CONTAINS toLower($keyword) OR " +
                    "toLower(" + endpointNameExprB + ") CONTAINS toLower($keyword)) " +
                    "RETURN head(labels(a)) AS aLabel, toString(a.id) AS aId, " +
                    endpointNameExprA + " AS aName, properties(a) AS aProps, " +
                    "head(labels(b)) AS bLabel, toString(b.id) AS bId, " +
                    endpointNameExprB + " AS bName, properties(b) AS bProps, type(r) AS relType " +
                    "LIMIT $limit";
        }

        return "MATCH p=(start)-[*1.." + depth + "]-(end) " +
                "WHERE start.id IS NOT NULL AND end.id IS NOT NULL " +
                "AND ($nodeLabel IS NULL OR any(n IN nodes(p) WHERE $nodeLabel IN labels(n))) " +
                "AND ($keyword IS NULL OR any(n IN nodes(p) WHERE toLower(" + nodeNameExpr + ") CONTAINS toLower($keyword))) " +
                "WITH p LIMIT $limit " +
                "UNWIND relationships(p) AS rel " +
                "WITH DISTINCT startNode(rel) AS a, rel AS r, endNode(rel) AS b " +
                "WHERE a.id IS NOT NULL AND b.id IS NOT NULL " +
                "AND ($relationshipType IS NULL OR type(r) = $relationshipType) " +
                "RETURN head(labels(a)) AS aLabel, toString(a.id) AS aId, " +
                endpointNameExprA + " AS aName, properties(a) AS aProps, " +
                "head(labels(b)) AS bLabel, toString(b.id) AS bId, " +
                endpointNameExprB + " AS bName, properties(b) AS bProps, type(r) AS relType " +
                "LIMIT $limit";
    }

    @Override
    public GraphQAResp answerGraphQuestion(GraphQAReq req) {
        DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
        DataSource neo4jDataSource = dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
        String question = safeTrim(req.getQuestion());
        int safeLimit = sanitizeLimit(req.getLimit(), 10, 50);
        GraphQAPlan plan = planGraphQuestion(question, safeLimit);

        List<Map<String, Object>> rows;
        try (Session session = databaseConnectionService.getNeo4jSession(neo4jDataSource)) {
            rows = session.run(plan.cypher, plan.parameters).list(record -> record.asMap());
        }

        GraphQAResp resp = new GraphQAResp();
        resp.setIntent(plan.intent);
        resp.setCount(rows.size());
        resp.setResults(rows);
        resp.setAnswer(formatGraphAnswer(plan.intent, question, rows));
        return resp;
    }

    @Override
    public GraphRAGResp answerGraphRAG(GraphRAGReq req, Long userId) {
        long totalStart = System.currentTimeMillis();
        DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
        DataSource neo4jDataSource = dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
        String question = sanitizeGraphRagQuestion(req.getQuestion());
        int safeLimit = sanitizeLimit(req.getLimit(), 12, GRAPH_RAG_MAX_LIMIT);
        int safeDepth = sanitizeDepth(req.getDepth());
        TraceHandle traceHandle = startGraphRagTrace(userId, req, safeLimit, safeDepth);
        TraceStepCollector stepCollector = new TraceStepCollector(traceFacade, traceHandle, traceSanitizer);

        List<Map<String, Object>> recognizedEntities;
        List<Map<String, Object>> graphSources;
        String keyword;
        List<String> searchTerms;
        List<String> preferredRelationships;
        long entityMs;
        long retrievalMs;
        long llmMs = 0L;
        long citationMs = 0L;
        try (Session session = databaseConnectionService.getNeo4jSession(neo4jDataSource)) {
            keyword = buildGraphRagKeyword(question);
            searchTerms = buildGraphRagSearchTerms(question);
            preferredRelationships = inferPreferredRelationships(question);
            long entityStart = System.currentTimeMillis();
            recognizedEntities = stepCollector.trace(
                    "GRAPH_ENTITY_RECOGNITION",
                    "图谱实体识别",
                    "keyword=" + keyword + ",terms=" + searchTerms + ",limit=" + Math.min(8, safeLimit),
                    () -> recognizeGraphEntities(session, question, keyword, searchTerms, Math.min(8, safeLimit)),
                    entities -> "entityCount=" + entities.size() + ",entities=" + formatEntitySummary(entities));
            entityMs = System.currentTimeMillis() - entityStart;
            warnIfSlow("GraphRAG实体识别", entityMs, "userId=" + userId + ",keyword=" + keyword);
            long retrievalStart = System.currentTimeMillis();
            graphSources = stepCollector.trace(
                    "GRAPH_CONTEXT_RETRIEVAL",
                    "图谱上下文召回",
                    "depth=" + safeDepth + ",limit=" + safeLimit + ",entityCount=" + recognizedEntities.size()
                            + ",preferredRelationships=" + preferredRelationships,
                    () -> session.run(buildGraphRagRetrievalCypher(safeDepth, !recognizedEntities.isEmpty(), !preferredRelationships.isEmpty()),
                            Values.parameters(
                                    "keyword", keyword,
                                    "terms", searchTerms,
                                    "question", question,
                                    "entities", recognizedEntities,
                                    "preferredRelationships", preferredRelationships,
                                    "limit", safeLimit))
                            .list(record -> record.asMap()),
                    sources -> "sourceCount=" + sources.size());
            retrievalMs = System.currentTimeMillis() - retrievalStart;
            warnIfSlow("GraphRAG图谱召回", retrievalMs,
                    "userId=" + userId + ",depth=" + safeDepth + ",limit=" + safeLimit + ",entityCount=" + recognizedEntities.size());
        } catch (RuntimeException e) {
            finishGraphRagTraceError(traceHandle, e);
            throw e;
        }
        graphSources = withCitationIds(graphSources);
        logger.info("GraphRAG召回完成 - userId: {}, entityCount: {}, graphHitCount: {}, depth: {}, limit: {}",
                userId, recognizedEntities.size(), graphSources.size(), safeDepth, safeLimit);

        GraphRAGResp resp = new GraphRAGResp();
        resp.setRecognizedEntities(recognizedEntities);
        resp.setGraphSources(graphSources);
        resp.setGraphHitCount(graphSources.size());
        resp.setMetrics(buildGraphRagMetrics(safeDepth, safeLimit, keyword, searchTerms, preferredRelationships,
                recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart));

        if (graphSources.isEmpty()) {
            resp.setLlmGenerated(false);
            resp.setCitationValid(true);
            resp.setAnswer("没有召回到可用于回答的图谱上下文。请尝试输入更具体的用户、知识库、文档、应用或模型名称。");
            resp.setMessage("graph_context_empty");
            resp.setErrorCode("GRAPH_CONTEXT_EMPTY");
            resp.setFallbackReason("未召回到图谱上下文");
            resp.setMetrics(buildGraphRagMetrics(safeDepth, safeLimit, keyword, searchTerms, preferredRelationships,
                    recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart));
            logger.info("GraphRAG无图谱上下文 - userId: {}, entityCount: {}", userId, recognizedEntities.size());
            finishGraphRagTraceSuccess(traceHandle, "graph_context_empty,entityCount=" + recognizedEntities.size());
            return resp;
        }

        QAModel qaModel = null;
        try {
            qaModel = stepCollector.trace(
                    "GRAPH_RAG_MODEL_RESOLVE",
                    "解析GraphRAG模型",
                    "modelId=" + req.getModelId(),
                    () -> resolveGraphRagModel(req.getModelId()),
                    model -> model == null ? "model=null" : "modelId=" + model.getId() + ",name=" + model.getName());
            resp.setModelId(qaModel.getId());
            resp.setModelName(qaModel.getName());

            modelLanguageModelFactory.setTraceSource("GraphRAG");
            if (traceHandle != null && traceHandle.getTraceId() != null && !traceHandle.getTraceId().isBlank()) {
                modelLanguageModelFactory.setTraceId(traceHandle.getTraceId());
            modelLanguageModelFactory.markBusinessTraceStarted();
            }
            ChatLanguageModel chatModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            List<dev.langchain4j.data.message.ChatMessage> messages = buildGraphRagMessages(question, graphSources);
            long llmStart = System.currentTimeMillis();
            Response<AiMessage> aiResponse = stepCollector.trace(
                    "GRAPH_RAG_LLM_GENERATE",
                    "GraphRAG模型生成",
                    "modelId=" + qaModel.getId() + ",graphHitCount=" + graphSources.size(),
                    () -> chatModel.generate(messages),
                    result -> result != null && result.content() != null ? "generated=true" : "generated=false");
            llmMs = System.currentTimeMillis() - llmStart;
            warnIfSlow("GraphRAG模型生成", llmMs, "userId=" + userId + ",modelId=" + qaModel.getId());
            String answer = aiResponse.content().text();
            long citationStart = System.currentTimeMillis();
            CitationValidationResult citationValidation = validateCitations(answer, graphSources);
            citationMs = System.currentTimeMillis() - citationStart;
            if (!citationValidation.valid) {
                logger.warn("GraphRAG引用校验失败，降级为结构化回答 - userId: {}, reason: {}", userId, citationValidation.message);
                resp.setAnswer(formatCitationFallbackAnswer(citationValidation.message, graphSources));
                resp.setLlmGenerated(false);
                resp.setCitationValid(false);
                resp.setMessage("citation_invalid: " + citationValidation.message);
                resp.setErrorCode("CITATION_INVALID");
                resp.setFallbackReason(citationValidation.message);
                resp.setMetrics(buildGraphRagMetrics(safeDepth, safeLimit, keyword, searchTerms, preferredRelationships,
                        recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart));
                finishGraphRagTraceSuccess(traceHandle,
                        buildGraphRagTraceSummary("citation_invalid,reason=" + citationValidation.message,
                                recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart)
                                + ",modelId=" + qaModel.getId());
                return resp;
            }
            resp.setAnswer(answer);
            resp.setLlmGenerated(true);
            resp.setCitationValid(true);
            resp.setMessage("ok");
            resp.setErrorCode(null);
            resp.setFallbackReason(null);
            resp.setMetrics(buildGraphRagMetrics(safeDepth, safeLimit, keyword, searchTerms, preferredRelationships,
                    recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart));
            logger.info("GraphRAG生成成功 - userId: {}, modelId: {}, entityCount: {}, graphHitCount: {}",
                    userId, qaModel.getId(), recognizedEntities.size(), graphSources.size());
            finishGraphRagTraceSuccess(traceHandle,
                    buildGraphRagTraceSummary("ok", recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart)
                            + ",modelId=" + qaModel.getId());
            return resp;
        } catch (Exception e) {
            logger.warn("GraphRAG LLM生成失败，降级为图谱结构化回答", e);
            resp.setLlmGenerated(false);
            if (qaModel != null) {
                resp.setModelId(qaModel.getId());
                resp.setModelName(qaModel.getName());
            }
            resp.setAnswer(formatGraphRagFallbackAnswer(question, graphSources));
            resp.setCitationValid(true);
            resp.setMessage("llm_fallback: " + (e.getMessage() != null ? e.getMessage() : "unknown"));
            resp.setErrorCode(classifyGraphRagErrorCode(e));
            resp.setFallbackReason(e.getMessage() != null ? e.getMessage() : "unknown");
            resp.setMetrics(buildGraphRagMetrics(safeDepth, safeLimit, keyword, searchTerms, preferredRelationships,
                    recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart));
            logger.info("GraphRAG降级为结构化回答 - userId: {}, modelId: {}, entityCount: {}, graphHitCount: {}, reason: {}",
                    userId, qaModel != null ? qaModel.getId() : null, recognizedEntities.size(), graphSources.size(), e.getMessage());
            finishGraphRagTraceSuccess(traceHandle,
                    buildGraphRagTraceSummary("llm_fallback", recognizedEntities, graphSources, entityMs, retrievalMs, llmMs, citationMs, totalStart)
                            + ",error=" + e.getMessage());
            return resp;
        } finally {
            modelLanguageModelFactory.clearTraceSource();
            modelLanguageModelFactory.clearTraceId();
            modelLanguageModelFactory.clearBusinessTraceStarted();
        }
    }

    private GraphNodeResp buildGraphNode(String id, String label, String name, Map<String, Object> properties) {
        GraphNodeResp n = new GraphNodeResp();
        n.setId(id);
        n.setLabel(label);
        n.setName(name);
        n.setProperties(properties);
        return n;
    }

    private TraceHandle startGraphRagTrace(Long userId, GraphRAGReq req, int limit, int depth) {
        try {
            TraceStartRequest startRequest = new TraceStartRequest();
            startRequest.setTraceSource("GraphRAG");
            startRequest.setUserId(userId);
            startRequest.setRequestType("graph_rag");
            startRequest.setRequestSummary("question=" + safeTrim(req.getQuestion()) + ",limit=" + limit + ",depth=" + depth
                    + ",modelId=" + req.getModelId());
            return traceFacade.start(startRequest);
        } catch (Exception e) {
            logger.debug("启动GraphRAG业务追踪失败，降级继续", e);
            return null;
        }
    }

    private String sanitizeGraphRagQuestion(String question) {
        String value = safeTrim(question);
        if (value == null) {
            throw new BusinessException("问题不能为空", ErrorCode.BAD_REQUEST);
        }
        if (value.length() > GRAPH_RAG_MAX_QUESTION_LENGTH) {
            throw new BusinessException("问题长度不能超过" + GRAPH_RAG_MAX_QUESTION_LENGTH + "个字符", ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    private String classifyGraphRagErrorCode(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("model") || message.contains("模型")) {
            return "MODEL_UNAVAILABLE";
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) {
            return "LLM_TIMEOUT";
        }
        if (message.contains("neo4j") || message.contains("cypher")) {
            return "GRAPH_QUERY_FAILED";
        }
        if (message.contains("connection") || message.contains("refused") || message.contains("reset")) {
            return "UPSTREAM_CONNECTION_FAILED";
        }
        return "GRAPH_RAG_FALLBACK";
    }

    private void finishGraphRagTraceSuccess(TraceHandle traceHandle, String summary) {
        if (traceHandle == null) {
            return;
        }
        try {
            traceFacade.success(traceHandle, summary);
        } catch (Exception e) {
            logger.debug("结束GraphRAG业务追踪失败，降级继续", e);
        }
    }

    private void finishGraphRagTraceError(TraceHandle traceHandle, Throwable error) {
        if (traceHandle == null) {
            return;
        }
        try {
            traceFacade.error(traceHandle, error);
        } catch (Exception e) {
            logger.debug("记录GraphRAG业务追踪异常失败，降级继续", e);
        }
    }

    private Map<String, Object> buildGraphRagMetrics(
            int depth,
            int limit,
            String keyword,
            List<String> searchTerms,
            List<String> preferredRelationships,
            List<Map<String, Object>> recognizedEntities,
            List<Map<String, Object>> graphSources,
            long entityMs,
            long retrievalMs,
            long llmMs,
            long citationMs,
            long totalStart) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("depth", depth);
        metrics.put("limit", limit);
        metrics.put("keyword", keyword);
        metrics.put("searchTerms", searchTerms);
        metrics.put("preferredRelationships", preferredRelationships);
        metrics.put("entityCount", recognizedEntities == null ? 0 : recognizedEntities.size());
        metrics.put("graphHitCount", graphSources == null ? 0 : graphSources.size());
        metrics.put("entityRecognitionMs", entityMs);
        metrics.put("graphRetrievalMs", retrievalMs);
        metrics.put("llmGenerationMs", llmMs);
        metrics.put("citationValidationMs", citationMs);
        metrics.put("totalMs", System.currentTimeMillis() - totalStart);
        return metrics;
    }

    private String buildGraphRagTraceSummary(
            String status,
            List<Map<String, Object>> recognizedEntities,
            List<Map<String, Object>> graphSources,
            long entityMs,
            long retrievalMs,
            long llmMs,
            long citationMs,
            long totalStart) {
        return status
                + ",entityCount=" + (recognizedEntities == null ? 0 : recognizedEntities.size())
                + ",graphHitCount=" + (graphSources == null ? 0 : graphSources.size())
                + ",entityRecognitionMs=" + entityMs
                + ",graphRetrievalMs=" + retrievalMs
                + ",llmGenerationMs=" + llmMs
                + ",citationValidationMs=" + citationMs
                + ",totalMs=" + (System.currentTimeMillis() - totalStart);
    }

    private void warnIfSlow(String operation, long durationMs, String detail) {
        long threshold = operation != null && operation.contains("模型") ? GRAPH_RAG_SLOW_MS : GRAPH_QUERY_SLOW_MS;
        if (durationMs >= threshold) {
            logger.warn("{} 慢操作 - durationMs: {}, thresholdMs: {}, {}", operation, durationMs, threshold, detail);
        }
    }

    private String formatEntitySummary(List<Map<String, Object>> entities) {
        if (entities == null || entities.isEmpty()) {
            return "[]";
        }
        return entities.stream()
                .limit(8)
                .map(entity -> entity.get("label") + ":" + entity.get("name") + "(score=" + entity.get("score") + ")")
                .collect(Collectors.joining("; "));
    }

    private List<Map<String, Object>> recognizeGraphEntities(Session session, String question, String keyword, List<String> terms, int limit) {
        String entityCypher = "MATCH (n) " +
                "WHERE n.id IS NOT NULL " +
                "WITH n, coalesce(n.username, n.name, n.title, n.originalFileName, n.model, toString(n.id)) AS entityName " +
                "WHERE entityName IS NOT NULL AND size(trim(entityName)) >= 2 " +
                "WITH n, entityName, " +
                "CASE " +
                "WHEN toLower($question) = toLower(entityName) THEN 100 " +
                "WHEN toLower($question) CONTAINS toLower(entityName) THEN 90 " +
                "WHEN $keyword IS NOT NULL AND toLower(entityName) = toLower($keyword) THEN 80 " +
                "WHEN $keyword IS NOT NULL AND toLower(entityName) CONTAINS toLower($keyword) THEN 60 " +
                "WHEN $keyword IS NOT NULL AND toLower($keyword) CONTAINS toLower(entityName) THEN 50 " +
                "WHEN any(term IN $terms WHERE toLower(entityName) = toLower(term)) THEN 70 " +
                "WHEN any(term IN $terms WHERE toLower(entityName) CONTAINS toLower(term)) THEN 45 " +
                "WHEN any(term IN $terms WHERE toLower(term) CONTAINS toLower(entityName) AND size(entityName) >= 3) THEN 35 " +
                "ELSE 0 END AS score " +
                "WHERE score > 0 " +
                "RETURN head(labels(n)) AS label, toString(n.id) AS id, entityName AS name, score AS score " +
                "ORDER BY score DESC, size(entityName) DESC LIMIT $limit";
        return session.run(entityCypher, Values.parameters(
                "question", question,
                "keyword", keyword,
                "terms", terms,
                "limit", limit
        )).list(record -> record.asMap());
    }

    private String buildGraphRagRetrievalCypher(int depth, boolean hasEntities, boolean hasPreferredRelationships) {
        String nodeNameExpr = "coalesce(n.username, n.name, n.title, n.originalFileName, n.model, toString(n.id))";
        String sourceNameExpr = "coalesce(a.username, a.name, a.title, a.originalFileName, a.model, toString(a.id))";
        String targetNameExpr = "coalesce(b.username, b.name, b.title, b.originalFileName, b.model, toString(b.id))";

        String seedMatch;
        if (hasEntities) {
            seedMatch = "MATCH (seed) WHERE any(entity IN $entities WHERE entity.label IN labels(seed) AND toString(seed.id) = entity.id) ";
        } else if (hasPreferredRelationships) {
            seedMatch = "MATCH (seed)-[seedRel]-() WHERE seed.id IS NOT NULL AND type(seedRel) IN $preferredRelationships WITH DISTINCT seed ";
        } else {
            seedMatch = "MATCH (seed) WHERE seed.id IS NOT NULL ";
        }

        String seedWhere = hasEntities
                ? ""
                : "AND (size($terms) = 0 OR size($preferredRelationships) > 0 OR any(term IN $terms WHERE toLower(coalesce(seed.username, seed.name, seed.title, seed.originalFileName, seed.model, toString(seed.id))) CONTAINS toLower(term))) ";

        return seedMatch +
                "MATCH p=(seed)-[*1.." + depth + "]-(end) " +
                "WHERE end.id IS NOT NULL " +
                seedWhere +
                "AND (size($terms) = 0 " +
                "OR any(n IN nodes(p) WHERE any(term IN $terms WHERE toLower(" + nodeNameExpr + ") CONTAINS toLower(term))) " +
                "OR any(pathRel IN relationships(p) WHERE type(pathRel) IN $preferredRelationships) " +
                "OR size($entities) > 0) " +
                "WITH p, length(p) AS pathLength LIMIT $limit " +
                "UNWIND relationships(p) AS rel " +
                "WITH startNode(rel) AS a, rel AS r, endNode(rel) AS b, min(pathLength) AS pathLength " +
                "WHERE a.id IS NOT NULL AND b.id IS NOT NULL " +
                "RETURN head(labels(a)) AS sourceLabel, toString(a.id) AS sourceId, " +
                sourceNameExpr + " AS sourceName, type(r) AS relationship, " +
                "head(labels(b)) AS targetLabel, toString(b.id) AS targetId, " +
                targetNameExpr + " AS targetName, pathLength AS pathLength, " +
                "(" +
                "100 - pathLength * 10 + " +
                "CASE WHEN $keyword IS NOT NULL AND toLower(" + sourceNameExpr + ") CONTAINS toLower($keyword) THEN 15 ELSE 0 END + " +
                "CASE WHEN $keyword IS NOT NULL AND toLower(" + targetNameExpr + ") CONTAINS toLower($keyword) THEN 15 ELSE 0 END + " +
                "CASE WHEN any(term IN $terms WHERE toLower(" + sourceNameExpr + ") CONTAINS toLower(term)) THEN 12 ELSE 0 END + " +
                "CASE WHEN any(term IN $terms WHERE toLower(" + targetNameExpr + ") CONTAINS toLower(term)) THEN 12 ELSE 0 END + " +
                "CASE WHEN type(r) IN $preferredRelationships THEN 20 ELSE 0 END + " +
                "CASE WHEN any(entity IN $entities WHERE entity.label IN labels(a) AND entity.id = toString(a.id)) THEN 30 ELSE 0 END + " +
                "CASE WHEN any(entity IN $entities WHERE entity.label IN labels(b) AND entity.id = toString(b.id)) THEN 30 ELSE 0 END" +
                ") AS score " +
                "ORDER BY score DESC, pathLength ASC " +
                "LIMIT $limit";
    }

    private List<Map<String, Object>> withCitationIds(List<Map<String, Object>> graphSources) {
        List<Map<String, Object>> cited = new ArrayList<>();
        for (int i = 0; i < graphSources.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>(graphSources.get(i));
            row.put("citationId", "G" + (i + 1));
            cited.add(row);
        }
        return cited;
    }

    private String buildGraphRagKeyword(String question) {
        String keyword = extractQuotedKeyword(question);
        if (keyword != null) {
            return keyword;
        }
        keyword = extractPlainKeyword(question);
        if (keyword != null) {
            return keyword;
        }
        return null;
    }

    private List<String> buildGraphRagSearchTerms(String question) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String quoted = extractQuotedKeyword(question);
        if (quoted != null) {
            terms.add(quoted);
        }
        String plain = extractPlainKeyword(question);
        if (plain != null) {
            terms.add(plain);
        }

        String normalized = normalizeQuestionForTerms(question);
        for (String token : normalized.split("\\s+")) {
            addGraphRagTerm(terms, token);
        }

        if (terms.isEmpty()) {
            String fallback = safeTrim(question);
            if (fallback != null && fallback.length() <= 40) {
                terms.add(fallback);
            }
        }
        return new ArrayList<>(terms).stream().limit(8).collect(Collectors.toList());
    }

    private String normalizeQuestionForTerms(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question
                .replaceAll("[，。！？；：、,.!?;:()（）\\[\\]【】{}<>《》\"'“”‘’]", " ")
                .replace("知识库", " 知识库 ")
                .replace("文档", " 文档 ")
                .replace("文件", " 文件 ")
                .replace("会话", " 会话 ")
                .replace("对话", " 对话 ")
                .replace("消息", " 消息 ")
                .replace("模型", " 模型 ")
                .replace("应用", " 应用 ")
                .replace("用户", " 用户 ");
        return normalized.trim();
    }

    private void addGraphRagTerm(Set<String> terms, String token) {
        String term = safeTrim(token);
        if (term == null) {
            return;
        }
        term = term.replaceAll("^(的|和|与|及|以及)+", "").replaceAll("(的|吗|呢|了)$", "").trim();
        if (term.length() < 2 || term.length() > 40) {
            return;
        }
        if (GRAPH_RAG_STOP_WORDS.contains(term.toLowerCase(Locale.ROOT)) || GRAPH_RAG_STOP_WORDS.contains(term)) {
            return;
        }
        terms.add(term);
    }

    private List<String> inferPreferredRelationships(String question) {
        LinkedHashSet<String> relationships = new LinkedHashSet<>();
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (containsAny(q, "创建", "creator", "created") && containsAny(q, "知识库", "kb")) {
            relationships.add("CREATED_KB");
        }
        if (containsAny(q, "文档", "文件", "document") && containsAny(q, "知识库", "kb")) {
            relationships.add("HAS_DOCUMENT");
        }
        if (containsAny(q, "会话", "对话", "conversation")) {
            relationships.add("HAS_CONVERSATION");
            relationships.add("HAS_MESSAGE");
        }
        if (containsAny(q, "应用", "app")) {
            relationships.add("HAS_APP");
            relationships.add("USING_APP");
        }
        if (containsAny(q, "模型", "model")) {
            relationships.add("USING_MODEL");
        }
        if (containsAny(q, "使用", "用了", "调用") && containsAny(q, "知识库", "kb")) {
            relationships.add("USING_KB");
        }
        return new ArrayList<>(relationships);
    }

    private QAModel resolveGraphRagModel(Long modelId) {
        if (modelId != null) {
            Optional<QAModel> optional = qaModelRepository.findById(modelId);
            if (!optional.isPresent()) {
                throw new BusinessException("问答模型不存在", ErrorCode.MODEL_NOT_FOUND);
            }
            QAModel model = optional.get();
            if (model.getDeleted() != null && model.getDeleted() == 1) {
                throw new BusinessException("问答模型已删除", ErrorCode.MODEL_NOT_FOUND);
            }
            if (model.getEnabled() == null || !model.getEnabled()) {
                throw new BusinessException("问答模型未启用", ErrorCode.MODEL_NOT_FOUND);
            }
            return model;
        }

        Optional<QAModel> defaultRag = qaModelRepository.findDefaultByUseFor("rag");
        if (defaultRag.isPresent()) {
            return defaultRag.get();
        }
        Optional<QAModel> defaultBoth = qaModelRepository.findDefaultByUseFor("both");
        if (defaultBoth.isPresent()) {
            return defaultBoth.get();
        }
        List<QAModel> ragModels = qaModelRepository.findByUseFor("rag");
        if (!ragModels.isEmpty()) {
            return ragModels.get(0);
        }
        List<QAModel> bothModels = qaModelRepository.findByUseFor("both");
        if (!bothModels.isEmpty()) {
            return bothModels.get(0);
        }
        throw new BusinessException("没有可用的GraphRAG问答模型", ErrorCode.MODEL_NOT_FOUND);
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildGraphRagMessages(String question, List<Map<String, Object>> graphSources) {
        String systemPrompt = SkillLoader.loadSkill("analytics/graph_rag_system_prompt");
        String userPrompt = SkillLoader.loadSkillWithTemplate("analytics/graph_rag_user_prompt_template", Map.of(
                "question", String.valueOf(question),
                "graphSources", formatGraphSourcesForPrompt(graphSources)));
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userPrompt));
        return messages;
    }

    private String formatGraphSourcesForPrompt(List<Map<String, Object>> graphSources) {
        StringBuilder sb = new StringBuilder();
        int max = Math.min(graphSources.size(), GRAPH_RAG_MAX_PROMPT_SOURCES);
        for (int i = 0; i < max; i++) {
            Map<String, Object> row = graphSources.get(i);
            sb.append("[")
                    .append(row.get("citationId"))
                    .append("] score=")
                    .append(row.get("score"))
                    .append(" pathLength=")
                    .append(row.get("pathLength"))
                    .append(" [")
                    .append(row.get("sourceLabel"))
                    .append(":")
                    .append(row.get("sourceName"))
                    .append("] -")
                    .append(row.get("relationship"))
                    .append("-> [")
                    .append(row.get("targetLabel"))
                    .append(":")
                    .append(row.get("targetName"))
                    .append("]\n");
        }
        if (graphSources.size() > max) {
            sb.append("... 还有 ").append(graphSources.size() - max).append(" 条图谱关系未列入提示词。\n");
        }
        return sb.toString();
    }

    private String formatGraphRagFallbackAnswer(String question, List<Map<String, Object>> graphSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("已召回 ").append(graphSources.size()).append(" 条图谱关系，但模型生成不可用，以下是结构化图谱依据：");
        int displayCount = Math.min(graphSources.size(), 10);
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> row = graphSources.get(i);
            sb.append("\n").append(i + 1).append(". ")
                    .append("[")
                    .append(row.get("citationId"))
                    .append("] ")
                    .append(row.get("sourceLabel")).append(":").append(row.get("sourceName"))
                    .append(" -").append(row.get("relationship")).append("-> ")
                    .append(row.get("targetLabel")).append(":").append(row.get("targetName"))
                    .append("，score=").append(row.get("score"));
        }
        if (graphSources.size() > displayCount) {
            sb.append("\n还有 ").append(graphSources.size() - displayCount).append(" 条关系未展示。");
        }
        return sb.toString();
    }

    private CitationValidationResult validateCitations(String answer, List<Map<String, Object>> graphSources) {
        if (answer == null || answer.trim().isEmpty()) {
            return CitationValidationResult.invalid("answer_empty");
        }
        Set<String> allowed = graphSources.stream()
                .map(row -> String.valueOf(row.get("citationId")))
                .collect(Collectors.toSet());
        Matcher matcher = CITATION_PATTERN.matcher(answer);
        Set<String> used = new LinkedHashSet<>();
        while (matcher.find()) {
            used.add("G" + matcher.group(1));
        }
        if (used.isEmpty()) {
            return CitationValidationResult.invalid("missing_citation");
        }
        for (String citation : used) {
            if (!allowed.contains(citation)) {
                return CitationValidationResult.invalid("unknown_citation:" + citation);
            }
        }
        return CitationValidationResult.valid();
    }

    private String formatCitationFallbackAnswer(String reason, List<Map<String, Object>> graphSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("模型回答未通过引用校验（").append(reason).append("），已切换为可核验的图谱依据：");
        int displayCount = Math.min(graphSources.size(), 10);
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> row = graphSources.get(i);
            sb.append("\n").append(i + 1).append(". ")
                    .append("[")
                    .append(row.get("citationId"))
                    .append("] ")
                    .append(row.get("sourceLabel")).append(":").append(row.get("sourceName"))
                    .append(" -").append(row.get("relationship")).append("-> ")
                    .append(row.get("targetLabel")).append(":").append(row.get("targetName"))
                    .append("，score=").append(row.get("score"));
        }
        if (graphSources.size() > displayCount) {
            sb.append("\n还有 ").append(graphSources.size() - displayCount).append(" 条关系未展示。");
        }
        return sb.toString();
    }

    private static class CitationValidationResult {
        private final boolean valid;
        private final String message;

        private CitationValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        private static CitationValidationResult valid() {
            return new CitationValidationResult(true, "ok");
        }

        private static CitationValidationResult invalid(String message) {
            return new CitationValidationResult(false, message);
        }
    }

    private GraphQAPlan planGraphQuestion(String question, int limit) {
        String q = question == null ? "" : question.trim();
        String lower = q.toLowerCase(Locale.ROOT);
        String keyword = extractQuotedKeyword(q);
        if (keyword == null) {
            keyword = extractPlainKeyword(q);
        }

        if (containsAny(q, "统计", "数量", "多少", "概览") && containsAny(q, "节点", "图谱", "label")) {
            return new GraphQAPlan(
                    "node_statistics",
                    "MATCH (n) " +
                            "WITH coalesce(head(labels(n)), 'Unknown') AS nodeLabel, count(n) AS nodeCount " +
                            "RETURN nodeLabel AS nodeLabel, nodeCount AS nodeCount " +
                            "ORDER BY nodeCount DESC LIMIT $limit",
                    Values.parameters("limit", limit)
            );
        }

        if (containsAny(q, "统计", "数量", "多少", "概览") && containsAny(q, "关系", "边", "relationship")) {
            return new GraphQAPlan(
                    "relationship_statistics",
                    "MATCH ()-[r]->() " +
                            "RETURN type(r) AS relationshipType, count(r) AS relationshipCount " +
                            "ORDER BY relationshipCount DESC LIMIT $limit",
                    Values.parameters("limit", limit)
            );
        }

        if (containsAny(q, "模型", "model") && containsAny(q, "最多", "使用", "排行", "排名")) {
            return new GraphQAPlan(
                    "model_usage",
                    "MATCH (msg:Message)-[:USING_MODEL]->(m:QAModel) " +
                            "RETURN m.id AS modelId, coalesce(m.name, m.model, toString(m.id)) AS modelName, " +
                            "m.provider AS provider, m.useFor AS useFor, count(msg) AS usageCount " +
                            "ORDER BY usageCount DESC LIMIT $limit",
                    Values.parameters("limit", limit)
            );
        }

        if (containsAny(q, "向量", "索引", "vector") && containsAny(q, "文档", "文件", "状态")) {
            return new GraphQAPlan(
                    "document_vectorization_status",
                    "MATCH (kb:KnowledgeBase)-[:HAS_DOCUMENT]->(d:KnowledgeDocument) " +
                            "WHERE $keyword IS NULL OR toLower(kb.name) CONTAINS toLower($keyword) " +
                            "OR toLower(d.originalFileName) CONTAINS toLower($keyword) " +
                            "RETURN kb.name AS knowledgeBaseName, d.id AS documentId, d.originalFileName AS documentName, " +
                            "d.fileType AS fileType, d.status AS status, d.vectorizedStatus AS vectorizedStatus, d.vectorizedError AS vectorizedError " +
                            "ORDER BY d.updateTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        if (containsAny(q, "文档", "文件") && containsAny(q, "知识库", "kb")) {
            return new GraphQAPlan(
                    "knowledge_base_documents",
                    "MATCH (kb:KnowledgeBase)-[:HAS_DOCUMENT]->(d:KnowledgeDocument) " +
                            "WHERE $keyword IS NULL OR toLower(kb.name) CONTAINS toLower($keyword) " +
                            "OR toLower(d.originalFileName) CONTAINS toLower($keyword) " +
                            "RETURN kb.id AS knowledgeBaseId, kb.name AS knowledgeBaseName, d.id AS documentId, " +
                            "d.originalFileName AS documentName, d.fileType AS fileType, d.status AS status, d.vectorizedStatus AS vectorizedStatus " +
                            "ORDER BY kb.name, d.createTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        if (containsAny(q, "应用", "app") && containsAny(q, "用户", "拥有", "使用", "哪些")) {
            return new GraphQAPlan(
                    "user_app_usage",
                    "MATCH (u:User)-[r:HAS_APP]->(a:AiApp) " +
                            "WHERE $keyword IS NULL OR toLower(u.username) CONTAINS toLower($keyword) " +
                            "OR toLower(a.name) CONTAINS toLower($keyword) " +
                            "RETURN u.id AS userId, u.username AS username, a.id AS appId, a.name AS appName, " +
                            "a.type AS appType, a.status AS appStatus, r.roleType AS roleType, r.status AS relationStatus " +
                            "ORDER BY u.username, a.updateTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        if (containsAny(q, "用户", "user") && containsAny(q, "知识库", "kb", "创建")) {
            return new GraphQAPlan(
                    "user_created_knowledge_bases",
                    "MATCH (u:User)-[:CREATED_KB]->(kb:KnowledgeBase) " +
                            "WHERE $keyword IS NULL OR toLower(u.username) CONTAINS toLower($keyword) " +
                            "OR toLower(kb.name) CONTAINS toLower($keyword) " +
                            "RETURN u.id AS userId, u.username AS username, kb.id AS knowledgeBaseId, kb.name AS knowledgeBaseName, " +
                            "kb.status AS status, kb.isPublic AS isPublic, kb.vectorStoreType AS vectorStoreType " +
                            "ORDER BY u.username, kb.createTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        if (containsAny(q, "会话", "对话", "conversation") && containsAny(q, "最近", "用户", "知识库")) {
            return new GraphQAPlan(
                    "recent_conversations",
                    "MATCH (u:User)-[:HAS_CONVERSATION]->(c:Conversation) " +
                            "OPTIONAL MATCH (c)-[:USING_KB]->(kb:KnowledgeBase) " +
                            "OPTIONAL MATCH (c)-[:USING_APP]->(a:AiApp) " +
                            "WHERE $keyword IS NULL OR toLower(u.username) CONTAINS toLower($keyword) " +
                            "OR toLower(c.title) CONTAINS toLower($keyword) " +
                            "OR toLower(kb.name) CONTAINS toLower($keyword) " +
                            "OR toLower(a.name) CONTAINS toLower($keyword) " +
                            "RETURN u.id AS userId, u.username AS username, c.id AS conversationId, c.title AS title, " +
                            "c.type AS type, c.createTimeMs AS createTimeMs, kb.name AS knowledgeBaseName, a.name AS appName " +
                            "ORDER BY c.createTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        if (containsAny(q, "知识库", "kb") && containsAny(q, "哪些", "列表", "所有", "多少")) {
            return new GraphQAPlan(
                    "knowledge_base_list",
                            "MATCH (kb:KnowledgeBase) " +
                            "OPTIONAL MATCH (kb)-[:HAS_DOCUMENT]->(d:KnowledgeDocument) " +
                            "WHERE $keyword IS NULL OR toLower(kb.name) CONTAINS toLower($keyword) " +
                            "WITH kb, count(d) AS documentCount " +
                            "RETURN kb.id AS knowledgeBaseId, kb.name AS knowledgeBaseName, kb.status AS status, " +
                            "kb.isPublic AS isPublic, kb.vectorStoreType AS vectorStoreType, documentCount AS documentCount " +
                            "ORDER BY kb.updateTimeMs DESC LIMIT $limit",
                    Values.parameters("keyword", keyword, "limit", limit)
            );
        }

        String searchKeyword = keyword != null ? keyword : q;
        if (lower.contains("graph")) {
            searchKeyword = keyword;
        }
        return new GraphQAPlan(
                "graph_search",
                "MATCH (a)-[r]->(b) " +
                        "WHERE $keyword IS NULL OR " +
                        "toLower(coalesce(a.username, a.name, a.title, a.originalFileName, a.model, toString(a.id))) CONTAINS toLower($keyword) OR " +
                        "toLower(coalesce(b.username, b.name, b.title, b.originalFileName, b.model, toString(b.id))) CONTAINS toLower($keyword) " +
                        "RETURN head(labels(a)) AS sourceLabel, coalesce(a.username, a.name, a.title, a.originalFileName, a.model, toString(a.id)) AS sourceName, " +
                        "type(r) AS relationship, head(labels(b)) AS targetLabel, " +
                        "coalesce(b.username, b.name, b.title, b.originalFileName, b.model, toString(b.id)) AS targetName " +
                        "LIMIT $limit",
                Values.parameters("keyword", safeTrim(searchKeyword), "limit", limit)
        );
    }

    private String formatGraphAnswer(String intent, String question, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "没有在当前图谱中找到匹配结果。可以换一个更具体的名称，例如用户、知识库或文档名。";
        }

        StringBuilder sb = new StringBuilder();
        if ("model_usage".equals(intent)) {
            sb.append("模型使用排行如下：");
        } else if ("node_statistics".equals(intent)) {
            sb.append("图谱节点统计如下：");
        } else if ("relationship_statistics".equals(intent)) {
            sb.append("图谱关系统计如下：");
        } else if ("document_vectorization_status".equals(intent)) {
            sb.append("文档向量化状态如下：");
        } else if ("knowledge_base_documents".equals(intent)) {
            sb.append("匹配到的知识库文档如下：");
        } else if ("user_app_usage".equals(intent)) {
            sb.append("用户应用关系如下：");
        } else if ("user_created_knowledge_bases".equals(intent)) {
            sb.append("用户创建知识库的匹配结果如下：");
        } else if ("recent_conversations".equals(intent)) {
            sb.append("最近会话匹配结果如下：");
        } else if ("knowledge_base_list".equals(intent)) {
            sb.append("知识库匹配结果如下：");
        } else {
            sb.append("我在图谱里找到这些相关关系：");
        }

        int displayCount = Math.min(rows.size(), 8);
        for (int i = 0; i < displayCount; i++) {
            sb.append("\n").append(i + 1).append(". ").append(formatRow(rows.get(i)));
        }
        if (rows.size() > displayCount) {
            sb.append("\n还有 ").append(rows.size() - displayCount).append(" 条结果未展示。");
        }
        return sb.toString();
    }

    private String formatRow(Map<String, Object> row) {
        return row.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("，"));
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractQuotedKeyword(String question) {
        if (question == null) {
            return null;
        }
        String[][] marks = {{"\"", "\""}, {"'", "'"}, {"“", "”"}, {"‘", "’"}, {"《", "》"}};
        for (String[] pair : marks) {
            int start = question.indexOf(pair[0]);
            int end = start >= 0 ? question.indexOf(pair[1], start + pair[0].length()) : -1;
            if (start >= 0 && end > start) {
                return safeTrim(question.substring(start + pair[0].length(), end));
            }
        }
        return null;
    }

    private String extractPlainKeyword(String question) {
        if (question == null) {
            return null;
        }
        String text = question
                .replace("有哪些", " ")
                .replace("哪些", " ")
                .replace("所有", " ")
                .replace("多少", " ")
                .replace("最近", " ")
                .replace("创建", " ")
                .replace("知识库", " ")
                .replace("文档", " ")
                .replace("文件", " ")
                .replace("用户", " ")
                .replace("会话", " ")
                .replace("对话", " ")
                .replace("模型", " ")
                .replace("使用", " ")
                .replace("排行", " ")
                .replace("？", " ")
                .replace("?", " ")
                .trim();
        if (text.length() < 2 || text.length() > 40) {
            return null;
        }
        return text;
    }

    private String normalizeGraphLabel(String nodeLabel) {
        String label = safeTrim(nodeLabel);
        if (label == null) {
            return null;
        }
        if (!GRAPH_LABELS.contains(label)) {
            throw new BusinessException("不支持的节点类型：" + label, ErrorCode.BAD_REQUEST);
        }
        return label;
    }

    private String normalizeRelationshipType(String relationshipType) {
        String type = safeTrim(relationshipType);
        if (type == null) {
            return null;
        }
        type = type.toUpperCase(Locale.ROOT);
        if (!GRAPH_RELATIONSHIP_TYPES.contains(type)) {
            throw new BusinessException("不支持的关系类型：" + type, ErrorCode.BAD_REQUEST);
        }
        return type;
    }

    private int sanitizeLimit(Integer limit, int defaultValue, int maxValue) {
        int safeLimit = limit == null ? defaultValue : limit;
        if (safeLimit <= 0) {
            safeLimit = defaultValue;
        }
        return Math.min(safeLimit, maxValue);
    }

    private int sanitizeDepth(Integer depth) {
        int safeDepth = depth == null ? 1 : depth;
        if (safeDepth < 1) {
            return 1;
        }
        return Math.min(safeDepth, 3);
    }

    private static class GraphQAPlan {
        private final String intent;
        private final String cypher;
        private final org.neo4j.driver.Value parameters;

        private GraphQAPlan(String intent, String cypher, org.neo4j.driver.Value parameters) {
            this.intent = intent;
            this.cypher = cypher;
            this.parameters = parameters;
        }
    }

    private void runInternal(Long userId, String username, boolean force) {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long start = System.currentTimeMillis();
        dataAnalysisConfigService.writeRuntimeStatus("running", "同步任务开始", start, null, null, userId, username);

        try {
            DataAnalysisSettingsResp settings = dataAnalysisConfigService.getSettings();
            if (!Boolean.TRUE.equals(settings.getEnabled()) && !force) {
                dataAnalysisConfigService.writeRuntimeStatus("never", "同步未启用", null, null, null, userId, username);
                return;
            }

            DataSource neo4jDataSource;
            try {
                neo4jDataSource = dataAnalysisConfigService.getValidatedNeo4jDataSourceOrThrow(settings);
            } catch (BusinessException ex) {
                dataAnalysisConfigService.writeRuntimeStatus("failed", ex.getMessage(), null, null, null, userId, username);
                return;
            }

            Map<String, Object> metrics = syncToNeo4j(neo4jDataSource);

            long duration = System.currentTimeMillis() - start;
            dataAnalysisConfigService.writeRuntimeStatus("success", "同步成功", null, System.currentTimeMillis(), duration, userId, username);
            dataAnalysisConfigService.writeMetrics(metrics, userId, username);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            String message = e.getMessage() != null ? e.getMessage() : "同步失败";
            logger.error("数据同步到Neo4j失败", e);
            dataAnalysisConfigService.writeRuntimeStatus("failed", message, null, null, duration, userId, username);
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


