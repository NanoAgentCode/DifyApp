package com.github.app.dify.chat.service.impl;

import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.auth.domain.User;
import com.github.app.dify.chat.repository.*;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.chat.req.ChatHistoryRequest;
import com.github.app.dify.chat.req.CreateConversationRequest;
import com.github.app.dify.chat.resp.*;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.common.resp.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
/**
 * 会话历史管理服务
 * 会话（Conversation）：一个完整的对话会话，包含多轮问答
 * 消息（Message）：会话中的单条消息，一问一答为一轮对话
 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {
    
    @Autowired
    private ChatConversationRepository conversationRepository;
    
    @Autowired
    private ChatMessageRepository messageRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AiAppRepository aiAppRepository;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    /**
     * 创建新会话
     */
    @Transactional
    @Override
    public ChatConversationResponse createConversation(Long userId, CreateConversationRequest request) {
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setAppId(request.getAppId());
        conversation.setKnowledgeBaseId(request.getKnowledgeBaseId());
        conversation.setType(request.getType() != null ? request.getType() : 1); // 默认为普通聊天
        conversation.setTitle(request.getTitle() != null && !request.getTitle().trim().isEmpty() 
                ? request.getTitle() : "新会话");
        Date now = new Date();
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversation.setDeleted(0);
        
        conversation = conversationRepository.save(conversation);
        
        return convertToResponse(conversation);
    }
    
    /**
     * 获取或创建会话（如果conversationId为空或不存在，则创建新会话）
     */
    @Transactional
    @Override
    public Long getOrCreateConversation(Long userId, Long conversationId, Integer type, Long appId, Long knowledgeBaseId, String firstQuestion) {
        if (conversationId != null) {
            Optional<ChatConversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent() && existing.get().getUserId().equals(userId) 
                    && (existing.get().getDeleted() == null || existing.get().getDeleted() == 0)) {
                // 更新会话时间
                ChatConversation conv = existing.get();
                conv.setUpdateTime(new Date());
                conversationRepository.save(conv);
                return conversationId;
            }
        }
        
        // 创建新会话
        ChatConversation conversation = new ChatConversation();
        conversation.setUserId(userId);
        conversation.setAppId(appId);
        conversation.setKnowledgeBaseId(knowledgeBaseId);
        conversation.setType(type != null ? type : 1);
        
        // 自动生成标题（基于第一条问题，最多50个字符）
        String title = "新会话";
        if (firstQuestion != null && !firstQuestion.trim().isEmpty()) {
            title = firstQuestion.length() > 50 ? firstQuestion.substring(0, 50) + "..." : firstQuestion;
        }
        conversation.setTitle(title);
        
        Date now = new Date();
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversation.setDeleted(0);
        
        conversation = conversationRepository.save(conversation);
        return conversation.getId();
    }
    
    /**
     * 保存消息
     */
    @Transactional
    @Override
    public void saveMessage(Long conversationId, String role, String content) {
        saveMessage(conversationId, role, content, null, null, null, null);
    }
    
    /**
     * 保存消息（带Token信息）
     */
    @Transactional
    @Override
    public void saveMessage(Long conversationId, String role, String content, Long modelId, Long promptTokens, Long completionTokens, Long totalTokens) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setModelId(modelId);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        
        // 获取当前对话的最大sequence
        Integer maxSequence = messageRepository.getMaxSequenceByConversationId(conversationId);
        message.setSequence(maxSequence + 1);
        
        message.setCreateTime(new Date());
        messageRepository.save(message);
        
        // 更新对话的更新时间和模型ID
        Optional<ChatConversation> conversation = conversationRepository.findById(conversationId);
        if (conversation.isPresent()) {
            ChatConversation conv = conversation.get();
            conv.setUpdateTime(new Date());
            // 如果会话还没有模型ID，且当前消息有模型ID，则设置会话的模型ID
            if (conv.getModelId() == null && modelId != null) {
                conv.setModelId(modelId);
            }
            conversationRepository.save(conv);
        }
    }
    
    /**
     * 获取我的会话列表（用户端）
     * 确保只返回当前用户的会话历史
     */
    @Override
    public PageResponse<ChatConversationResponse> getMyConversations(Long userId, ChatHistoryRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), 
                Sort.by(Sort.Direction.DESC, "updateTime"));
        
        // 使用Specification动态构建查询，确保始终按userId过滤
        Specification<ChatConversation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 必须条件：只查询当前用户的会话
            predicates.add(cb.equal(root.get("userId"), userId));
            
            // 未删除的条件
            Predicate notDeleted = cb.or(
                cb.isNull(root.get("deleted")),
                cb.equal(root.get("deleted"), 0)
            );
            predicates.add(notDeleted);
            
            // 类型筛选
            if (request.getType() != null) {
                predicates.add(cb.equal(root.get("type"), request.getType()));
            }
            
            // 关键词搜索
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                predicates.add(cb.like(
                    cb.lower(root.get("title")),
                    "%" + request.getKeyword().toLowerCase() + "%"
                ));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        Page<ChatConversation> page = conversationRepository.findAll(spec, pageable);
        
        List<ChatConversationResponse> responses = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return new PageResponse<>(responses, page.getTotalElements(), 
                request.getPage(), request.getSize());
    }
    
    /**
     * 获取所有会话列表（管理员端）
     * 注意：此方法返回所有用户的会话历史，不限制为特定用户
     * 如果 request.getUserId() 不为 null，则只返回该用户的会话（用于筛选）
     */
    @Override
    public PageResponse<ChatConversationResponse> getAllConversations(ChatHistoryRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), 
                Sort.by(Sort.Direction.DESC, "createTime"));
        
        // 使用Specification动态构建查询，避免PostgreSQL的NULL参数类型问题
        Specification<ChatConversation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 未删除的条件
            Predicate notDeleted = cb.or(
                cb.isNull(root.get("deleted")),
                cb.equal(root.get("deleted"), 0)
            );
            predicates.add(notDeleted);
            
            // 用户ID筛选（可选，如果指定则只返回该用户的会话，否则返回所有用户的会话）
            if (request.getUserId() != null) {
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            }
            
            // 类型筛选
            if (request.getType() != null) {
                predicates.add(cb.equal(root.get("type"), request.getType()));
            }
            
            // 关键词搜索
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                predicates.add(cb.like(
                    cb.lower(root.get("title")),
                    "%" + request.getKeyword().toLowerCase() + "%"
                ));
            }
            
            // 开始时间筛选
            if (request.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createTime"),
                    request.getStartTime()
                ));
            }
            
            // 结束时间筛选
            if (request.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("createTime"),
                    request.getEndTime()
                ));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        
        Page<ChatConversation> page = conversationRepository.findAll(spec, pageable);
        
        List<ChatConversationResponse> responses = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return new PageResponse<>(responses, page.getTotalElements(), 
                request.getPage(), request.getSize());
    }
    
    /**
     * 获取会话详情
     */
    @Override
    public ChatConversationResponse getConversation(Long conversationId, Long userId, boolean isAdmin) {
        Optional<ChatConversation> conversation;
        if (isAdmin) {
            conversation = conversationRepository.findById(conversationId);
        } else {
            conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        }
        
        if (!conversation.isPresent() || 
            (conversation.get().getDeleted() != null && conversation.get().getDeleted() == 1)) {
            throw new RuntimeException("会话不存在或已删除");
        }
        
        return convertToResponse(conversation.get());
    }
    
    /**
     * 获取会话消息列表（该会话中的所有对话消息）
     */
    @Override
    public List<ChatMessageResponse> getMessages(Long conversationId, Long userId, boolean isAdmin) {
        // 验证会话是否存在且属于该用户（管理员除外）
        if (!isAdmin) {
            Optional<ChatConversation> conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
            if (!conversation.isPresent() || 
                (conversation.get().getDeleted() != null && conversation.get().getDeleted() == 1)) {
                throw new RuntimeException("会话不存在或已删除");
            }
        }
        
        List<ChatMessage> messages = messageRepository.findByConversationId(conversationId);
        return messages.stream()
                .map(this::convertToMessageResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 更新会话标题
     */
    @Transactional
    @Override
    public void updateConversationTitle(Long conversationId, Long userId, String title, boolean isAdmin) {
        Optional<ChatConversation> conversation;
        if (isAdmin) {
            conversation = conversationRepository.findById(conversationId);
        } else {
            conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        }
        
        if (!conversation.isPresent() || 
            (conversation.get().getDeleted() != null && conversation.get().getDeleted() == 1)) {
            throw new RuntimeException("会话不存在或已删除");
        }
        
        ChatConversation conv = conversation.get();
        conv.setTitle(title);
        conv.setUpdateTime(new Date());
        conversationRepository.save(conv);
    }
    
    /**
     * 删除会话（软删除，会删除该会话中的所有消息）
     */
    @Transactional
    @Override
    public void deleteConversation(Long conversationId, Long userId, boolean isAdmin) {
        Optional<ChatConversation> conversation;
        if (isAdmin) {
            conversation = conversationRepository.findById(conversationId);
        } else {
            conversation = conversationRepository.findByIdAndUserId(conversationId, userId);
        }
        
        if (!conversation.isPresent() || 
            (conversation.get().getDeleted() != null && conversation.get().getDeleted() == 1)) {
            throw new RuntimeException("会话不存在或已删除");
        }
        
        ChatConversation conv = conversation.get();
        conv.setDeleted(1);
        conv.setUpdateTime(new Date());
        conversationRepository.save(conv);
    }
    
    /**
     * 批量删除会话（管理员）- 性能优化：使用批量更新
     */
    @Transactional
    @Override
    public void batchDeleteConversations(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return;
        }
        
        // 批量查询
        List<ChatConversation> conversations = conversationRepository.findAllById(conversationIds);
        Date now = new Date();
        
        // 批量更新
        for (ChatConversation conv : conversations) {
            conv.setDeleted(1);
            conv.setUpdateTime(now);
        }
        
        // 批量保存（JPA会自动批量处理）
        conversationRepository.saveAll(conversations);
    }
    
    /**
     * 导出会话（JSON格式，包含该会话中的所有消息）
     */
    @Override
    public Map<String, Object> exportConversation(Long conversationId, Long userId, boolean isAdmin) {
        ChatConversationResponse conversation = getConversation(conversationId, userId, isAdmin);
        List<ChatMessageResponse> messages = getMessages(conversationId, userId, isAdmin);
        
        Map<String, Object> export = new HashMap<>();
        export.put("conversation", conversation);
        export.put("messages", messages);
        export.put("exportTime", new Date());
        return export;
    }
    
    /**
     * 获取统计信息（管理员）
     */
    @Override
    public ChatHistoryStatisticsResponse getStatistics() {
        // 默认30天
        return getStatistics(30);
    }
    
    @Override
    public ChatHistoryStatisticsResponse getStatistics(Integer days) {
        if (days == null || days <= 0) {
            days = 30; // 默认30天
        }
        // 限制最大天数为90天
        if (days > 90) {
            days = 90;
        }
        
        ChatHistoryStatisticsResponse response = new ChatHistoryStatisticsResponse();
        
        // 总对话数
        Long totalConversations = conversationRepository.countAll();
        response.setTotalConversations(totalConversations);
        
        // 总消息数
        Long totalMessages = messageRepository.count();
        response.setTotalMessages(totalMessages);
        
        // 用户对话数排行（前10名）- 性能优化：使用批量查询避免N+1
        List<ChatHistoryStatisticsResponse.UserConversationRank> ranks = new ArrayList<>();
        List<Object[]> userCounts = conversationRepository.countByUserIdGroupBy();
        Map<Long, Long> userIdToCountMap = userCounts.stream()
                .collect(Collectors.toMap(
                    arr -> ((Number) arr[0]).longValue(),
                    arr -> ((Number) arr[1]).longValue()
                ));
        
        // 批量查询用户信息
        List<Long> userIds = new ArrayList<>(userIdToCountMap.keySet());
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        for (Map.Entry<Long, Long> entry : userIdToCountMap.entrySet()) {
            User user = userMap.get(entry.getKey());
            if (user != null) {
                ChatHistoryStatisticsResponse.UserConversationRank rank = 
                        new ChatHistoryStatisticsResponse.UserConversationRank();
                rank.setUserId(entry.getKey());
                rank.setUsername(user.getUsername());
                rank.setConversationCount(entry.getValue());
                ranks.add(rank);
            }
        }
        ranks.sort((a, b) -> Long.compare(b.getConversationCount(), a.getConversationCount()));
        response.setUserConversationRanks(ranks.stream().limit(10).collect(Collectors.toList()));
        
        // 对话类型分布 - 性能优化：使用分组查询
        Map<String, Long> typeDistribution = new HashMap<>();
        List<Object[]> typeCounts = conversationRepository.countByTypeGroupBy();
        for (Object[] arr : typeCounts) {
            Integer type = (Integer) arr[0];
            Long count = ((Number) arr[1]).longValue();
            String typeKey = type == 1 ? "普通聊天" : "知识库问答";
            typeDistribution.put(typeKey, count);
        }
        response.setTypeDistribution(typeDistribution);
        
        // 热门问题统计（前10个）- 性能优化：使用数据库查询而不是加载所有数据
        List<ChatHistoryStatisticsResponse.PopularQuestion> popularQuestions = new ArrayList<>();
        // 注意：热门问题统计需要加载用户消息内容，如果数据量很大，可以考虑使用数据库聚合查询
        // 这里暂时保持原逻辑，但建议在数据量大时使用数据库层面的聚合查询
        List<ChatMessage> userMessages = messageRepository.findAll().stream()
                .filter(m -> "user".equals(m.getRole()))
                .collect(Collectors.toList());
        
        Map<String, Long> questionCount = new HashMap<>();
        for (ChatMessage msg : userMessages) {
            String content = msg.getContent();
            if (content != null && content.length() > 0) {
                // 截取前100个字符作为问题标识
                String questionKey = content.length() > 100 ? content.substring(0, 100) : content;
                questionCount.put(questionKey, questionCount.getOrDefault(questionKey, 0L) + 1);
            }
        }
        
        questionCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .forEach(entry -> {
                    ChatHistoryStatisticsResponse.PopularQuestion pq = 
                            new ChatHistoryStatisticsResponse.PopularQuestion();
                    pq.setQuestion(entry.getKey());
                    pq.setCount(entry.getValue());
                    popularQuestions.add(pq);
                });
        response.setPopularQuestions(popularQuestions);
        
        // 时间趋势（最近N天）
        List<ChatHistoryStatisticsResponse.DailyStatistics> dailyStats = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        for (int i = days - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_MONTH, -i);
            Date date = cal.getTime();
            String dateStr = sdf.format(date);
            
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date startOfDay = cal.getTime();
            
            cal.add(Calendar.DAY_OF_MONTH, 1);
            Date endOfDay = cal.getTime();
            
            // 统计当天的对话数和消息数 - 性能优化：使用数据库查询而不是加载所有数据
            Long dayConversationCount = conversationRepository.countByDateRange(startOfDay, endOfDay);
            Long dayMessageCount = messageRepository.countByDateRange(startOfDay, endOfDay);
            
            ChatHistoryStatisticsResponse.DailyStatistics daily = 
                    new ChatHistoryStatisticsResponse.DailyStatistics();
            daily.setDate(dateStr);
            daily.setConversationCount(dayConversationCount != null ? dayConversationCount : 0L);
            daily.setMessageCount(dayMessageCount != null ? dayMessageCount : 0L);
            dailyStats.add(daily);
        }
        response.setDailyStatistics(dailyStats);
        
        return response;
    }
    
    /**
     * 转换会话实体为响应对象
     */
    private ChatConversationResponse convertToResponse(ChatConversation conversation) {
        ChatConversationResponse response = new ChatConversationResponse();
        response.setId(conversation.getId());
        response.setUserId(conversation.getUserId());
        response.setAppId(conversation.getAppId());
        response.setKnowledgeBaseId(conversation.getKnowledgeBaseId());
        response.setType(conversation.getType());
        response.setTitle(conversation.getTitle());
        response.setCreateTime(conversation.getCreateTime());
        response.setUpdateTime(conversation.getUpdateTime());
        
        // 获取用户名 - 性能优化：可以考虑批量查询，但单个会话查询影响不大
        Optional<User> user = userRepository.findById(conversation.getUserId());
        if (user.isPresent()) {
            response.setUsername(user.get().getUsername());
        }
        
        // 获取应用名称
        if (conversation.getAppId() != null) {
            aiAppRepository.findById(conversation.getAppId()).ifPresent(app -> {
                response.setAppName(app.getName());
            });
        }
        
        // 获取知识库名称
        if (conversation.getKnowledgeBaseId() != null) {
            knowledgeBaseRepository.findById(conversation.getKnowledgeBaseId()).ifPresent(kb -> {
                response.setKnowledgeBaseName(kb.getName());
            });
        }
        
        // 获取对话轮数（用户消息数，一问一答为一轮）
        Long conversationRounds = messageRepository.countConversationRoundsByConversationId(conversation.getId());
        response.setMessageCount(conversationRounds);
        
        return response;
    }
    
    /**
     * 转换消息实体为响应对象
     */
    private ChatMessageResponse convertToMessageResponse(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setConversationId(message.getConversationId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setSequence(message.getSequence());
        response.setCreateTime(message.getCreateTime());
        return response;
    }
}