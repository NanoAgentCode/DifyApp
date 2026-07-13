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
public class GraphQaService {

    private static final Logger logger = LoggerFactory.getLogger(GraphQaService.class);
    private static final long GRAPH_QUERY_SLOW_MS = 1500L;
    private static final Set<String> GRAPH_LABELS = new LinkedHashSet<>(Arrays.asList(
            "User", "AiApp", "KnowledgeBase", "KnowledgeDocument", "Conversation", "Message", "QAModel"
    ));
    private static final Set<String> GRAPH_RELATIONSHIP_TYPES = new LinkedHashSet<>(Arrays.asList(
            "HAS_APP", "CREATED_KB", "HAS_DOCUMENT", "HAS_CONVERSATION", "USING_APP", "USING_KB", "HAS_MESSAGE", "USING_MODEL"
    ));

    @Autowired
    private DataAnalysisConfigService dataAnalysisConfigService;

    @Autowired
    private DatabaseConnectionService databaseConnectionService;

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


    private GraphNodeResp buildGraphNode(String id, String label, String name, Map<String, Object> properties) {
        GraphNodeResp n = new GraphNodeResp();
        n.setId(id);
        n.setLabel(label);
        n.setName(name);
        n.setProperties(properties);
        return n;
    }

    private void warnIfSlow(String operation, long durationMs, String detail) {
        if (durationMs > GRAPH_QUERY_SLOW_MS) {
            logger.warn("{}耗时过长: {}ms, {}", operation, durationMs, detail);
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


    private String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
