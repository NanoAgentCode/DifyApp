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
public class GraphRagService {

    private static final Logger logger = LoggerFactory.getLogger(GraphRagService.class);
    private static final long GRAPH_QUERY_SLOW_MS = 1500L;
    private static final long GRAPH_RAG_SLOW_MS = 5000L;
    private static final int GRAPH_RAG_MAX_QUESTION_LENGTH = 500;
    private static final int GRAPH_RAG_MAX_PROMPT_SOURCES = 50;
    private static final int GRAPH_RAG_MAX_LIMIT = 80;
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[G(\\d+)]");
    private static final Set<String> GRAPH_RAG_STOP_WORDS = new HashSet<>(Arrays.asList(
            "哪些", "哪个", "什么", "怎么", "如何", "是否", "有没有", "有多少", "多少", "最近", "相关", "关联",
            "分别", "以及", "和", "与", "的", "了", "吗", "呢", "请", "帮我", "一下", "列出", "查看", "查询",
            "用户", "知识库", "文档", "文件", "会话", "对话", "消息", "模型", "应用", "图谱", "关系", "节点",
            "user", "kb", "app", "model", "graph", "rag"
    ));

    @Autowired
    private DataAnalysisConfigService dataAnalysisConfigService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    @Autowired
    private QAModelRepository qaModelRepository;

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Autowired
    private TraceFacade traceFacade;

    @Autowired
    private TraceSanitizer traceSanitizer;

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

    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
