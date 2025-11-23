package com.github.app.dify.service;

import com.github.app.dify.domain.ChatConversation;
import com.github.app.dify.domain.ChatMessage;
import com.github.app.dify.domain.User;
import com.github.app.dify.repository.*;
import com.github.app.dify.req.ChatHistoryRequest;
import com.github.app.dify.req.CreateConversationRequest;
import com.github.app.dify.resp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话历史管理服务
 * 会话（Conversation）：一个完整的对话会话，包含多轮问答
 * 消息（Message）：会话中的单条消息，一问一答为一轮对话
 */
@Service
public class ChatHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryService.class);
    
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
    public void saveMessage(Long conversationId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        
        // 获取当前对话的最大sequence
        Integer maxSequence = messageRepository.getMaxSequenceByConversationId(conversationId);
        message.setSequence(maxSequence + 1);
        
        message.setCreateTime(new Date());
        messageRepository.save(message);
        
        // 更新对话的更新时间
        Optional<ChatConversation> conversation = conversationRepository.findById(conversationId);
        if (conversation.isPresent()) {
            ChatConversation conv = conversation.get();
            conv.setUpdateTime(new Date());
            conversationRepository.save(conv);
        }
    }
    
    /**
     * 获取我的会话列表（用户端）
     */
    public PageResponse<ChatConversationResponse> getMyConversations(Long userId, ChatHistoryRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize());
        Page<ChatConversation> page;
        
        if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
            page = conversationRepository.searchByUserIdAndKeyword(userId, request.getKeyword(), pageable);
        } else if (request.getType() != null) {
            page = conversationRepository.findByUserIdAndType(userId, request.getType(), pageable);
        } else {
            page = conversationRepository.findByUserId(userId, pageable);
        }
        
        List<ChatConversationResponse> responses = page.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        
        return new PageResponse<>(responses, page.getTotalElements(), 
                request.getPage(), request.getSize());
    }
    
    /**
     * 获取所有会话列表（管理员端）
     */
    public PageResponse<ChatConversationResponse> getAllConversations(ChatHistoryRequest request) {
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize());
        
        // 使用Specification动态构建查询，避免PostgreSQL的NULL参数类型问题
        Specification<ChatConversation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 未删除的条件
            Predicate notDeleted = cb.or(
                cb.isNull(root.get("deleted")),
                cb.equal(root.get("deleted"), 0)
            );
            predicates.add(notDeleted);
            
            // 用户ID筛选
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
     * 批量删除会话（管理员）
     */
    @Transactional
    public void batchDeleteConversations(List<Long> conversationIds) {
        for (Long id : conversationIds) {
            Optional<ChatConversation> conversation = conversationRepository.findById(id);
            if (conversation.isPresent()) {
                ChatConversation conv = conversation.get();
                conv.setDeleted(1);
                conv.setUpdateTime(new Date());
                conversationRepository.save(conv);
            }
        }
    }
    
    /**
     * 导出会话（JSON格式，包含该会话中的所有消息）
     */
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
    public ChatHistoryStatisticsResponse getStatistics() {
        ChatHistoryStatisticsResponse response = new ChatHistoryStatisticsResponse();
        
        // 总对话数
        Long totalConversations = conversationRepository.countAll();
        response.setTotalConversations(totalConversations);
        
        // 总消息数
        Long totalMessages = messageRepository.count();
        response.setTotalMessages(totalMessages);
        
        // 用户对话数排行（前10名）
        List<ChatHistoryStatisticsResponse.UserConversationRank> ranks = new ArrayList<>();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            Long count = conversationRepository.countByUserId(user.getId());
            if (count > 0) {
                ChatHistoryStatisticsResponse.UserConversationRank rank = 
                        new ChatHistoryStatisticsResponse.UserConversationRank();
                rank.setUserId(user.getId());
                rank.setUsername(user.getUsername());
                rank.setConversationCount(count);
                ranks.add(rank);
            }
        }
        ranks.sort((a, b) -> Long.compare(b.getConversationCount(), a.getConversationCount()));
        response.setUserConversationRanks(ranks.stream().limit(10).collect(Collectors.toList()));
        
        // 对话类型分布
        Map<String, Long> typeDistribution = new HashMap<>();
        List<ChatConversation> allConversations = conversationRepository.findAll();
        for (ChatConversation conv : allConversations) {
            if (conv.getDeleted() == null || conv.getDeleted() == 0) {
                String typeKey = conv.getType() == 1 ? "普通聊天" : "知识库问答";
                typeDistribution.put(typeKey, typeDistribution.getOrDefault(typeKey, 0L) + 1);
            }
        }
        response.setTypeDistribution(typeDistribution);
        
        // 热门问题统计（前10个）
        List<ChatHistoryStatisticsResponse.PopularQuestion> popularQuestions = new ArrayList<>();
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
        
        // 时间趋势（最近30天）
        List<ChatHistoryStatisticsResponse.DailyStatistics> dailyStats = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        
        for (int i = 29; i >= 0; i--) {
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
            
            // 统计当天的对话数和消息数
            List<ChatConversation> dayConversations = conversationRepository.findAll().stream()
                    .filter(c -> c.getCreateTime() != null 
                            && c.getCreateTime().after(startOfDay) 
                            && c.getCreateTime().before(endOfDay)
                            && (c.getDeleted() == null || c.getDeleted() == 0))
                    .collect(Collectors.toList());
            
            List<ChatMessage> dayMessages = messageRepository.findAll().stream()
                    .filter(m -> m.getCreateTime() != null 
                            && m.getCreateTime().after(startOfDay) 
                            && m.getCreateTime().before(endOfDay))
                    .collect(Collectors.toList());
            
            ChatHistoryStatisticsResponse.DailyStatistics daily = 
                    new ChatHistoryStatisticsResponse.DailyStatistics();
            daily.setDate(dateStr);
            daily.setConversationCount((long) dayConversations.size());
            daily.setMessageCount((long) dayMessages.size());
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
        
        // 获取用户名
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

