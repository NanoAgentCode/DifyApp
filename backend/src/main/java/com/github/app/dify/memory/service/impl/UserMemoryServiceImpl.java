package com.github.app.dify.memory.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.memory.domain.UserMemory;
import com.github.app.dify.memory.repository.UserMemoryRepository;
import com.github.app.dify.memory.resp.UserMemoryItemResp;
import com.github.app.dify.memory.service.UserMemoryService;
import com.github.app.dify.memory.util.MemoryDateTimeUtil;
import com.github.app.dify.system.util.SkillLoader;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class UserMemoryServiceImpl implements UserMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(UserMemoryServiceImpl.class);

    private static final String TYPE_LONG_TERM = "long_term";
    private static final String TYPE_ENTITY = "entity";

    private static final String SCOPE_CHAT = "chat";
    private static final String SCOPE_KNOWLEDGE_BASE = "knowledge_base";
    private static final String SCOPE_APP = "app";

    private static final int MAX_RECENT_LONG_TERM = 80;
    private static final int MAX_RECENT_ENTITY = 80;
    private static final int MAX_MEMORY_ITEMS_PER_TYPE = 200;

    private static final int MAX_CONTEXT_ITEMS_LONG_TERM = 8;
    private static final int MAX_CONTEXT_ITEMS_ENTITY = 8;
    private static final int MAX_CONTEXT_CHARS_PER_ITEM = 400;
    /** 记忆上下文总字符数上限，避免挤占对话与系统提示空间 */
    private static final int MAX_CONTEXT_TOTAL_CHARS = 4096;

    @Autowired
    private UserMemoryRepository userMemoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QAModelRepository qaModelRepository;

    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;

    @Override
    public String buildMemoryContext(Long userId, String question, String scopeType, Long scopeId) {
        if (userId == null) {
            return "";
        }
        Scope scope = normalizeScope(scopeType, scopeId);
        String q = question != null ? question.trim() : "";
        List<String> tokens = tokenizeForMatch(q);

        List<UserMemory> longTerms = findScopedAndGlobalCandidates(userId, scope, TYPE_LONG_TERM, MAX_RECENT_LONG_TERM);
        List<UserMemory> entities = findScopedAndGlobalCandidates(userId, scope, TYPE_ENTITY, MAX_RECENT_ENTITY);

        List<UserMemory> pickedLong = pickBest(longTerms, tokens, MAX_CONTEXT_ITEMS_LONG_TERM);
        List<UserMemory> pickedEnt = pickBest(entities, tokens, MAX_CONTEXT_ITEMS_ENTITY);

        if (pickedLong.isEmpty() && pickedEnt.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【用户记忆】\n");
        int totalChars = sb.length();
        final int footerReserve = 256;
        int contentBudget = Math.max(0, MAX_CONTEXT_TOTAL_CHARS - footerReserve);

        if (!pickedLong.isEmpty()) {
            sb.append("【长期记忆】\n");
            totalChars = sb.length();
            for (UserMemory m : pickedLong) {
                if (totalChars >= contentBudget) {
                    break;
                }
                String line = safeOneLine(m.getContent());
                if (line.isEmpty()) {
                    continue;
                }
                String item = "- " + formatMemoryLine(m, line) + "\n";
                if (totalChars + item.length() > contentBudget) {
                    break;
                }
                sb.append(item);
                totalChars += item.length();
            }
        }
        if (totalChars < contentBudget && !pickedEnt.isEmpty()) {
            sb.append("【实体记忆】\n");
            totalChars = sb.length();
            for (UserMemory m : pickedEnt) {
                if (totalChars >= contentBudget) {
                    break;
                }
                String line = safeOneLine(m.getContent());
                if (line.isEmpty()) {
                    continue;
                }
                String item = "- " + formatMemoryLine(m, line) + "\n";
                if (totalChars + item.length() > contentBudget) {
                    break;
                }
                sb.append(item);
                totalChars += item.length();
            }
        }
        sb.append("\n请在不暴露记忆原文来源的前提下，利用以上信息进行个性化回答；若记忆与问题无关，请忽略。");
        sb.append("若多条记忆涉及同一类偏好、习惯或计划（如饮食、运动等），以列表中靠前的表述为准（靠前的为更新后的表述），旧表述视为已被取代，不要依据旧表述做推荐。");
        markMemoryAccessed(pickedLong);
        markMemoryAccessed(pickedEnt);
        return sb.toString();
    }

    @Async
    @Override
    public void updateMemoryAsync(Long userId, String question, String answer, Long modelId, Long conversationId,
            String scopeType, Long scopeId) {
        if (userId == null) {
            return;
        }
        Scope scope = normalizeScope(scopeType, scopeId);
        String q = question != null ? question.trim() : "";
        String a = answer != null ? answer.trim() : "";
        if (q.isEmpty() || a.isEmpty()) {
            return;
        }
        try {
            Optional<QAModel> modelOpt = modelId != null ? qaModelRepository.findById(modelId) : Optional.empty();
            if (modelOpt.isEmpty()) {
                modelOpt = qaModelRepository.findDefaultByUseFor("chat");
            }
            if (modelOpt.isEmpty()) {
                modelOpt = qaModelRepository.findDefaultByUseFor("both");
            }
            if (modelOpt.isEmpty()) {
                logger.debug("找不到可用模型，跳过记忆更新 - userId={}", userId);
                return;
            }

            QAModel qaModel = modelOpt.get();
            if (qaModel.getDeleted() != null && qaModel.getDeleted() == 1) {
                return;
            }
            if (qaModel.getEnabled() == null || !qaModel.getEnabled()) {
                return;
            }

            modelLanguageModelFactory.setTraceSource("User Memory Extraction");
            ChatLanguageModel model = modelLanguageModelFactory.createChatLanguageModel(qaModel);
            modelLanguageModelFactory.clearTraceSource();
            String extraction = extractMemoryJson(model, q, a);
            if (extraction == null || extraction.trim().isEmpty()) {
                return;
            }
            upsertFromJson(userId, scope, extraction, conversationId);
            enforceLimit(userId, scope, TYPE_LONG_TERM);
            enforceLimit(userId, scope, TYPE_ENTITY);
        } catch (Exception e) {
            logger.debug("异步记忆更新失败 - userId={}", userId, e);
        }
    }

    @Override
    @Transactional
    public void clearUserMemory(Long userId, String scopeType, Long scopeId) {
        if (userId == null) {
            return;
        }
        if (scopeType == null || scopeType.trim().isEmpty()) {
            userMemoryRepository.softDeleteAllByUserId(userId);
            return;
        }
        Scope scope = normalizeScope(scopeType, scopeId);
        userMemoryRepository.softDeleteAllByUserIdAndScope(userId, scope.type, scope.id);
    }

    @Override
    public List<UserMemoryItemResp> listUserMemory(Long userId, String memoryType, int page, int size, String scopeType,
            Long scopeId) {
        if (userId == null) {
            return List.of();
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage - 1, safeSize);

        List<UserMemory> items;
        String type = memoryType != null ? memoryType.trim() : null;
        boolean hasScope = scopeType != null && !scopeType.trim().isEmpty();
        if (hasScope) {
            Scope scope = normalizeScope(scopeType, scopeId);
            if (type == null || type.isEmpty()) {
                items = userMemoryRepository.findRecentByUserIdAndScope(userId, scope.type, scope.id, pageable);
            } else if (TYPE_LONG_TERM.equals(type) || TYPE_ENTITY.equals(type)) {
                items = userMemoryRepository.findRecentByUserIdAndScopeAndType(userId, scope.type, scope.id, type,
                        pageable);
            } else {
                items = List.of();
            }
        } else {
            if (type == null || type.isEmpty()) {
                items = userMemoryRepository.findRecentByUserId(userId, pageable);
            } else if (TYPE_LONG_TERM.equals(type) || TYPE_ENTITY.equals(type)) {
                items = userMemoryRepository.findRecentByUserIdAndType(userId, type, pageable);
            } else {
                items = List.of();
            }
        }

        List<UserMemoryItemResp> resp = new ArrayList<>();
        for (UserMemory m : items) {
            UserMemoryItemResp r = new UserMemoryItemResp();
            r.setId(m.getId());
            r.setScopeType(m.getScopeType());
            r.setScopeId(m.getScopeId());
            r.setMemoryType(m.getMemoryType());
            r.setMemoryKey(m.getMemoryKey());
            r.setContent(m.getContent());
            r.setImportance(m.getImportance());
            r.setUpdateTime(m.getUpdateTime());
            r.setFirstSeenTime(m.getFirstSeenTime());
            r.setLastMentionedTime(m.getLastMentionedTime());
            r.setLastAccessedTime(m.getLastAccessedTime());
            r.setAccessCount(m.getAccessCount());
            r.setSourceConversationId(m.getSourceConversationId());
            resp.add(r);
        }
        return resp;
    }

    @Override
    @Transactional
    public void deleteUserMemoryItem(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            throw new NotFoundException("记忆不存在");
        }
        int updated = userMemoryRepository.softDeleteByIdAndUserId(itemId, userId);
        if (updated <= 0) {
            throw new NotFoundException("记忆不存在");
        }
    }

    @Override
    @Transactional
    public void deleteUserMemoryItemAsAdmin(Long itemId) {
        if (itemId == null) {
            throw new NotFoundException("记忆不存在");
        }
        int updated = userMemoryRepository.softDeleteById(itemId);
        if (updated <= 0) {
            throw new NotFoundException("记忆不存在");
        }
    }

    private String extractMemoryJson(ChatLanguageModel model, String question, String answer) {
        List<ChatMessage> messages = new ArrayList<>();
        String system = SkillLoader.loadSkill("memory/extract_system_prompt");
        messages.add(SystemMessage.from(system));

        String user = SkillLoader.loadSkillWithTemplate("memory/extract_user_prompt_template", Map.of(
                "questionJson", objectMapper.valueToTree(question).toString(),
                "answerJson", objectMapper.valueToTree(answer).toString()));
        messages.add(UserMessage.from(user));

        Response<AiMessage> resp = model.generate(messages);
        String text = resp != null && resp.content() != null ? resp.content().text() : null;
        if (text == null) {
            return null;
        }
        return text.trim();
    }

    @Transactional
    protected void upsertFromJson(Long userId, Scope scope, String json, Long conversationId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            String trimmed = json.trim();
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String maybe = trimmed.substring(start, end + 1);
                try {
                    root = objectMapper.readTree(maybe);
                } catch (Exception ex) {
                    throw new BusinessException("JSON解析失败", ErrorCode.DATA_VALIDATION_FAILED, ex);
                }
            } else {
                throw new BusinessException("JSON解析失败", ErrorCode.DATA_VALIDATION_FAILED, e);
            }
        }
        if (root == null || !root.isObject()) {
            return;
        }

        JsonNode facts = root.get("long_term_facts");
        if (facts != null && facts.isArray()) {
            for (JsonNode node : facts) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String key = asText(node.get("key"));
                String content = asText(node.get("content"));
                Integer importance = asInt(node.get("importance"));
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }
                String stableKey = key != null && !key.trim().isEmpty() ? key.trim() : sha1Short(content);
                upsertOne(userId, scope, TYPE_LONG_TERM, stableKey, content.trim(), clampImportance(importance),
                        conversationId);
            }
        }

        JsonNode entities = root.get("entities");
        if (entities != null && entities.isArray()) {
            for (JsonNode node : entities) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String type = asText(node.get("type"));
                String name = asText(node.get("name"));
                JsonNode attrs = node.get("attributes");
                if (type == null || type.trim().isEmpty() || name == null || name.trim().isEmpty()) {
                    continue;
                }
                if (attrs != null && !attrs.isObject()) {
                    continue;
                }
                String key = (type.trim() + ":" + name.trim());
                String content;
                try {
                    content = objectMapper.writeValueAsString(node);
                } catch (Exception e) {
                    continue;
                }
                upsertOne(userId, scope, TYPE_ENTITY, key, content, 3, conversationId);
            }
        }
    }

    private void upsertOne(Long userId, Scope scope, String type, String key, String content, Integer importance,
            Long conversationId) {
        Optional<UserMemory> existing = userMemoryRepository.findActiveByUserIdAndScopeAndTypeAndKey(userId, scope.type,
                scope.id, type, key);
        UserMemory m;
        Date now = new Date();
        if (existing.isPresent()) {
            m = existing.get();
            m.setContent(content);
            m.setImportance(importance);
            m.setLastMentionedTime(now);
            if (conversationId != null) {
                m.setSourceConversationId(conversationId);
            }
            MemoryDateTimeUtil.setUpdateTime(m);
        } else {
            m = new UserMemory();
            m.setUserId(userId);
            m.setScopeType(scope.type);
            m.setScopeId(scope.id);
            m.setMemoryType(type);
            m.setMemoryKey(key);
            m.setContent(content);
            m.setImportance(importance);
            m.setFirstSeenTime(now);
            m.setLastMentionedTime(now);
            m.setAccessCount(0);
            m.setSourceConversationId(conversationId);
            m.setDeleted(0);
            MemoryDateTimeUtil.setCreateAndUpdateTime(m);
        }
        userMemoryRepository.save(m);
    }

    @Transactional
    protected void enforceLimit(Long userId, Scope scope, String type) {
        long count = userMemoryRepository.countActiveByUserIdAndScopeAndType(userId, scope.type, scope.id, type);
        if (count <= MAX_MEMORY_ITEMS_PER_TYPE) {
            return;
        }
        long toRemove = count - MAX_MEMORY_ITEMS_PER_TYPE;
        if (toRemove <= 0) {
            return;
        }
        List<UserMemory> oldest = userMemoryRepository.findOldestActiveByUserIdAndScopeAndType(
                userId, scope.type, scope.id, type, PageRequest.of(0, (int) Math.min(toRemove, 200)));
        if (oldest.isEmpty()) {
            return;
        }
        for (UserMemory m : oldest) {
            m.setDeleted(1);
            MemoryDateTimeUtil.setUpdateTime(m);
        }
        userMemoryRepository.saveAll(oldest);
    }

    private static class Scope {
        final String type;
        final Long id;

        private Scope(String type, Long id) {
            this.type = type;
            this.id = id;
        }
    }

    private Scope normalizeScope(String scopeType, Long scopeId) {
        String type = scopeType != null ? scopeType.trim().toLowerCase(Locale.ROOT) : "";
        if (type.isEmpty()) {
            return new Scope(SCOPE_CHAT, null);
        }
        if (SCOPE_KNOWLEDGE_BASE.equals(type) || SCOPE_APP.equals(type)) {
            return new Scope(type, scopeId);
        }
        if (SCOPE_CHAT.equals(type)) {
            return new Scope(SCOPE_CHAT, null);
        }
        return new Scope(SCOPE_CHAT, null);
    }

    private List<UserMemory> findScopedAndGlobalCandidates(Long userId, Scope scope, String type, int limit) {
        LinkedHashMap<Long, UserMemory> byId = new LinkedHashMap<>();
        addCandidates(byId, userMemoryRepository.findRecentByUserIdAndScopeAndType(
                userId, scope.type, scope.id, type, PageRequest.of(0, limit)));
        if (!SCOPE_CHAT.equals(scope.type)) {
            addCandidates(byId, userMemoryRepository.findRecentByUserIdAndScopeAndType(
                    userId, SCOPE_CHAT, null, type, PageRequest.of(0, limit)));
        }
        return new ArrayList<>(byId.values());
    }

    private void addCandidates(Map<Long, UserMemory> byId, List<UserMemory> items) {
        if (items == null) {
            return;
        }
        for (UserMemory item : items) {
            if (item != null && item.getId() != null) {
                byId.putIfAbsent(item.getId(), item);
            }
        }
    }

    /** 相关性优先，其次按重要度与时间排序，用于挑选记忆时的优先级 */
    private static final Comparator<ScoredMemory> MEMORY_SCORE = Comparator
            .comparingDouble(ScoredMemory::score)
            .reversed()
            .thenComparing(sm -> sm.memory().getUpdateTime(), Comparator.nullsLast(Comparator.reverseOrder()));

    private List<UserMemory> pickBest(List<UserMemory> candidates, List<String> tokens, int max) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (max <= 0) {
            return List.of();
        }
        List<ScoredMemory> scored = new ArrayList<>();
        for (UserMemory m : candidates) {
            scored.add(new ScoredMemory(m, scoreMemory(m, tokens)));
        }
        scored.sort(MEMORY_SCORE);
        List<UserMemory> out = new ArrayList<>();
        for (ScoredMemory sm : scored) {
            if (out.size() >= max) {
                break;
            }
            out.add(sm.memory());
        }
        return out;
    }

    private double scoreMemory(UserMemory memory, List<String> tokens) {
        double score = 0.0;
        score += (memory.getImportance() != null ? memory.getImportance() : 0) * 3.0;
        score += recencyScore(memory.getLastMentionedTime() != null
                ? memory.getLastMentionedTime()
                : memory.getUpdateTime()) * 2.0;
        score += recencyScore(memory.getLastAccessedTime()) * 0.5;

        if (tokens != null && !tokens.isEmpty()) {
            String haystack = ((memory.getMemoryKey() != null ? memory.getMemoryKey() : "") + " "
                    + (memory.getContent() != null ? memory.getContent() : "")).toLowerCase(Locale.ROOT);
            int hits = 0;
            int strongHits = 0;
            for (String token : tokens) {
                if (token == null || token.length() < 2) {
                    continue;
                }
                if (haystack.contains(token)) {
                    hits++;
                    if (token.length() >= 4) {
                        strongHits++;
                    }
                }
            }
            score += hits * 4.0 + strongHits * 2.0;
        }
        return score;
    }

    private double recencyScore(Date date) {
        if (date == null) {
            return 0.0;
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - date.getTime());
        double ageDays = ageMillis / 86_400_000.0;
        return Math.max(0.0, 1.0 / (1.0 + ageDays / 30.0));
    }

    private record ScoredMemory(UserMemory memory, double score) {
    }

    private List<String> tokenizeForMatch(String text) {
        if (text == null) {
            return List.of();
        }
        String t = text.toLowerCase(Locale.ROOT);
        String[] parts = t.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String s = p.trim();
            if (s.length() >= 2) {
                out.add(s);
                if (containsChinese(s) && s.length() > 4) {
                    addChineseNgrams(out, s);
                }
            }
        }
        return out;
    }

    private boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }
        return false;
    }

    private void addChineseNgrams(List<String> out, String text) {
        int maxNgram = Math.min(4, text.length());
        for (int n = 2; n <= maxNgram; n++) {
            for (int i = 0; i + n <= text.length(); i++) {
                String gram = text.substring(i, i + n);
                if (!out.contains(gram)) {
                    out.add(gram);
                }
            }
        }
    }

    private String formatMemoryLine(UserMemory memory, String line) {
        String content = trimToMax(line, MAX_CONTEXT_CHARS_PER_ITEM);
        String timeHint = buildTimeHint(memory);
        if (timeHint.isEmpty()) {
            return content;
        }
        return content + "（" + timeHint + "）";
    }

    private String buildTimeHint(UserMemory memory) {
        List<String> parts = new ArrayList<>();
        if (memory.getLastMentionedTime() != null) {
            parts.add("最近提及: " + formatTime(memory.getLastMentionedTime()));
        } else if (memory.getUpdateTime() != null) {
            parts.add("更新时间: " + formatTime(memory.getUpdateTime()));
        }
        if (memory.getFirstSeenTime() != null) {
            parts.add("首次记录: " + formatTime(memory.getFirstSeenTime()));
        }
        return String.join(", ", parts);
    }

    private String formatTime(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
    }

    private void markMemoryAccessed(List<UserMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (UserMemory memory : memories) {
            memory.setLastAccessedTime(now);
            memory.setAccessCount((memory.getAccessCount() != null ? memory.getAccessCount() : 0) + 1);
        }
        userMemoryRepository.saveAll(memories);
    }

    private String safeOneLine(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replace("\r", " ").replace("\n", " ").replace("\t", " ");
    }

    private String trimToMax(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private Integer asInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer clampImportance(Integer importance) {
        if (importance == null) {
            return 2;
        }
        if (importance < 0) {
            return 0;
        }
        if (importance > 5) {
            return 5;
        }
        return importance;
    }

    private String sha1Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
