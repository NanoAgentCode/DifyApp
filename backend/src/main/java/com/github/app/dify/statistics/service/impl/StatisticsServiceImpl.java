package com.github.app.dify.statistics.service.impl;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.chat.domain.AiApp;
import com.github.app.dify.chat.domain.ChatConversation;
import com.github.app.dify.chat.repository.AiAppRepository;
import com.github.app.dify.chat.domain.ChatMessage;
import com.github.app.dify.chat.repository.ChatConversationRepository;
import com.github.app.dify.chat.repository.ChatMessageRepository;
import com.github.app.dify.knowledgebase.domain.KnowledgeBase;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.statistics.resp.StatisticsResponse;
import com.github.app.dify.statistics.service.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务实现类
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AiAppRepository aiAppRepository;
    
    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    @Autowired
    private ChatConversationRepository chatConversationRepository;
    
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    @Autowired
    private QAModelRepository qaModelRepository;
    
    @Override
    public StatisticsResponse.OverviewStatistics getOverviewStatistics() {
        StatisticsResponse.OverviewStatistics overview = new StatisticsResponse.OverviewStatistics();
        
        // 用户总数（未删除）
        long totalUsers = userRepository.findAll().stream()
                .filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
                .count();
        overview.setTotalUsers(totalUsers);
        
        // 应用总数（未删除）
        long totalApps = aiAppRepository.findAll().stream()
                .filter(a -> a.getDeleted() == null || a.getDeleted() == 0)
                .count();
        overview.setTotalApps(totalApps);
        
        // 知识库总数（未删除）
        long totalKnowledgeBases = knowledgeBaseRepository.findAll().stream()
                .filter(kb -> kb.getDeleted() == null || kb.getDeleted() == 0)
                .count();
        overview.setTotalKnowledgeBases(totalKnowledgeBases);
        
        // 会话总数（未删除）
        overview.setTotalConversations(chatConversationRepository.countAll());
        
        // 消息总数
        overview.setTotalMessages(chatMessageRepository.count());
        
        return overview;
    }
    
    @Override
    public StatisticsResponse.UserStatistics getUserStatistics() {
        StatisticsResponse.UserStatistics userStats = new StatisticsResponse.UserStatistics();
        
        // 用户总数
        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
                .collect(Collectors.toList());
        userStats.setTotal((long) allUsers.size());
        
        // 角色分布
        Map<String, Long> roleDistribution = new HashMap<>();
        for (User user : allUsers) {
            String roleKey = user.getRole() == 1 ? "管理员" : "普通用户";
            roleDistribution.put(roleKey, roleDistribution.getOrDefault(roleKey, 0L) + 1);
        }
        userStats.setRoleDistribution(roleDistribution);
        
        // 状态分布
        Map<String, Long> statusDistribution = new HashMap<>();
        for (User user : allUsers) {
            String statusKey;
            if (user.getStatus() == null) {
                statusKey = "未知";
            } else if (user.getStatus() == 0) {
                statusKey = "待审核";
            } else if (user.getStatus() == 1) {
                statusKey = "已激活";
            } else if (user.getStatus() == 2) {
                statusKey = "已禁用";
            } else {
                statusKey = "未知";
            }
            statusDistribution.put(statusKey, statusDistribution.getOrDefault(statusKey, 0L) + 1);
        }
        userStats.setStatusDistribution(statusDistribution);
        
        // 注册趋势（最近N天，默认30天）
        int days = 30; // 默认30天
        return getUserStatistics(days);
    }
    
    @Override
    public StatisticsResponse.UserStatistics getUserStatistics(Integer days) {
        if (days == null || days <= 0) {
            days = 30; // 默认30天
        }
        // 限制最大天数为90天
        if (days > 90) {
            days = 90;
        }
        
        StatisticsResponse.UserStatistics userStats = new StatisticsResponse.UserStatistics();
        
        // 用户总数
        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.getDeleted() == null || u.getDeleted() == 0)
                .collect(Collectors.toList());
        userStats.setTotal((long) allUsers.size());
        
        // 角色分布
        Map<String, Long> roleDistribution = new HashMap<>();
        for (User user : allUsers) {
            String roleKey = user.getRole() == 1 ? "管理员" : "普通用户";
            roleDistribution.put(roleKey, roleDistribution.getOrDefault(roleKey, 0L) + 1);
        }
        userStats.setRoleDistribution(roleDistribution);
        
        // 状态分布
        Map<String, Long> statusDistribution = new HashMap<>();
        for (User user : allUsers) {
            String statusKey;
            if (user.getStatus() == null) {
                statusKey = "未知";
            } else if (user.getStatus() == 0) {
                statusKey = "待审核";
            } else if (user.getStatus() == 1) {
                statusKey = "已激活";
            } else if (user.getStatus() == 2) {
                statusKey = "已禁用";
            } else {
                statusKey = "未知";
            }
            statusDistribution.put(statusKey, statusDistribution.getOrDefault(statusKey, 0L) + 1);
        }
        userStats.setStatusDistribution(statusDistribution);
        
        // 注册趋势（最近N天）
        List<StatisticsResponse.DailyCount> registrationTrend = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        
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
            
            long count = allUsers.stream()
                    .filter(u -> u.getCreateTime() != null
                            && u.getCreateTime().after(startOfDay)
                            && u.getCreateTime().before(endOfDay))
                    .count();
            
            StatisticsResponse.DailyCount dailyCount = new StatisticsResponse.DailyCount();
            dailyCount.setDate(dateStr);
            dailyCount.setCount(count);
            registrationTrend.add(dailyCount);
        }
        userStats.setRegistrationTrend(registrationTrend);
        
        return userStats;
    }
    
    @Override
    public StatisticsResponse.AppStatistics getAppStatistics() {
        StatisticsResponse.AppStatistics appStats = new StatisticsResponse.AppStatistics();
        
        // 应用总数
        List<AiApp> allApps = aiAppRepository.findAll().stream()
                .filter(a -> a.getDeleted() == null || a.getDeleted() == 0)
                .collect(Collectors.toList());
        appStats.setTotal((long) allApps.size());
        
        // 类型分布
        Map<String, Long> typeDistribution = new HashMap<>();
        for (AiApp app : allApps) {
            String typeKey = app.getType() == 1 ? "Chat Flow" : "Workflow";
            typeDistribution.put(typeKey, typeDistribution.getOrDefault(typeKey, 0L) + 1);
        }
        appStats.setTypeDistribution(typeDistribution);
        
        // 应用使用情况（按应用分组统计会话数）
        List<ChatConversation> allConversations = chatConversationRepository.findAll().stream()
                .filter(c -> (c.getDeleted() == null || c.getDeleted() == 0) && c.getAppId() != null)
                .collect(Collectors.toList());
        
        Map<Long, Long> appUsageMap = new HashMap<>();
        for (ChatConversation conv : allConversations) {
            appUsageMap.put(conv.getAppId(), appUsageMap.getOrDefault(conv.getAppId(), 0L) + 1);
        }
        
        List<StatisticsResponse.AppUsage> appUsage = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : appUsageMap.entrySet()) {
            Long appId = entry.getKey();
            Long count = entry.getValue();
            
            AiApp app = allApps.stream()
                    .filter(a -> a.getId().equals(appId))
                    .findFirst()
                    .orElse(null);
            
            if (app != null) {
                StatisticsResponse.AppUsage usage = new StatisticsResponse.AppUsage();
                usage.setAppId(appId);
                usage.setAppName(app.getName());
                usage.setConversationCount(count);
                appUsage.add(usage);
            }
        }
        
        // 按会话数排序，取前10
        appUsage.sort((a, b) -> Long.compare(b.getConversationCount(), a.getConversationCount()));
        appStats.setAppUsage(appUsage.stream().limit(10).collect(Collectors.toList()));
        
        return appStats;
    }
    
    @Override
    public StatisticsResponse.KnowledgeBaseStatistics getKnowledgeBaseStatistics() {
        StatisticsResponse.KnowledgeBaseStatistics kbStats = new StatisticsResponse.KnowledgeBaseStatistics();
        
        // 知识库总数
        List<KnowledgeBase> allKbs = knowledgeBaseRepository.findAll().stream()
                .filter(kb -> kb.getDeleted() == null || kb.getDeleted() == 0)
                .collect(Collectors.toList());
        kbStats.setTotal((long) allKbs.size());
        
        // 状态分布
        Map<String, Long> statusDistribution = new HashMap<>();
        for (KnowledgeBase kb : allKbs) {
            String statusKey = (kb.getStatus() != null && kb.getStatus() == 1) ? "启用" : "禁用";
            statusDistribution.put(statusKey, statusDistribution.getOrDefault(statusKey, 0L) + 1);
        }
        kbStats.setStatusDistribution(statusDistribution);
        
        // 知识库使用情况（按知识库分组统计会话数）
        List<ChatConversation> allConversations = chatConversationRepository.findAll().stream()
                .filter(c -> (c.getDeleted() == null || c.getDeleted() == 0) && c.getKnowledgeBaseId() != null)
                .collect(Collectors.toList());
        
        Map<Long, Long> kbUsageMap = new HashMap<>();
        for (ChatConversation conv : allConversations) {
            kbUsageMap.put(conv.getKnowledgeBaseId(), kbUsageMap.getOrDefault(conv.getKnowledgeBaseId(), 0L) + 1);
        }
        
        List<StatisticsResponse.KnowledgeBaseUsage> kbUsage = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : kbUsageMap.entrySet()) {
            Long kbId = entry.getKey();
            Long count = entry.getValue();
            
            KnowledgeBase kb = allKbs.stream()
                    .filter(k -> k.getId().equals(kbId))
                    .findFirst()
                    .orElse(null);
            
            if (kb != null) {
                StatisticsResponse.KnowledgeBaseUsage usage = new StatisticsResponse.KnowledgeBaseUsage();
                usage.setKbId(kbId);
                usage.setKbName(kb.getName());
                usage.setConversationCount(count);
                kbUsage.add(usage);
            }
        }
        
        // 按会话数排序，取前10
        kbUsage.sort((a, b) -> Long.compare(b.getConversationCount(), a.getConversationCount()));
        kbStats.setKbUsage(kbUsage.stream().limit(10).collect(Collectors.toList()));
        
        return kbStats;
    }
    
    @Override
    public StatisticsResponse.ModelTokenStatistics getModelTokenStatistics() {
        // 默认30天
        return getModelTokenStatistics(30);
    }
    
    @Override
    public StatisticsResponse.ModelTokenStatistics getModelTokenStatistics(Integer days) {
        if (days == null || days <= 0) {
            days = 30; // 默认30天
        }
        // 限制最大天数为90天
        if (days > 90) {
            days = 90;
        }
        
        StatisticsResponse.ModelTokenStatistics tokenStats = new StatisticsResponse.ModelTokenStatistics();
        
        // 获取所有有token信息的消息
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        List<ChatMessage> messagesWithTokens = allMessages.stream()
                .filter(m -> m.getTotalTokens() != null && m.getTotalTokens() > 0)
                .collect(Collectors.toList());
        
        // 总Token数
        long totalTokens = messagesWithTokens.stream()
                .mapToLong(m -> m.getTotalTokens() != null ? m.getTotalTokens() : 0L)
                .sum();
        tokenStats.setTotalTokens(totalTokens);
        
        // 获取所有模型
        List<QAModel> allModels = qaModelRepository.findAll().stream()
                .filter(m -> m.getDeleted() == null || m.getDeleted() == 0)
                .collect(Collectors.toList());
        
        // 按模型分组统计token使用量
        Map<Long, StatisticsResponse.ModelTokenUsage> modelUsageMap = new HashMap<>();
        
        for (ChatMessage msg : messagesWithTokens) {
            if (msg.getModelId() != null) {
                StatisticsResponse.ModelTokenUsage usage = modelUsageMap.computeIfAbsent(
                    msg.getModelId(),
                    k -> {
                        StatisticsResponse.ModelTokenUsage u = new StatisticsResponse.ModelTokenUsage();
                        u.setModelId(k);
                        // 查找模型名称
                        QAModel model = allModels.stream()
                                .filter(m -> m.getId().equals(k))
                                .findFirst()
                                .orElse(null);
                        u.setModelName(model != null ? model.getName() : "未知模型");
                        u.setPromptTokens(0L);
                        u.setCompletionTokens(0L);
                        u.setTotalTokens(0L);
                        return u;
                    }
                );
                
                // 累加token数量
                if (msg.getPromptTokens() != null) {
                    usage.setPromptTokens(usage.getPromptTokens() + msg.getPromptTokens());
                }
                if (msg.getCompletionTokens() != null) {
                    usage.setCompletionTokens(usage.getCompletionTokens() + msg.getCompletionTokens());
                }
                if (msg.getTotalTokens() != null) {
                    usage.setTotalTokens(usage.getTotalTokens() + msg.getTotalTokens());
                }
            }
        }
        
        // 转换为列表并排序
        List<StatisticsResponse.ModelTokenUsage> modelTokenUsage = new ArrayList<>(modelUsageMap.values());
        modelTokenUsage.sort((a, b) -> Long.compare(b.getTotalTokens(), a.getTotalTokens()));
        tokenStats.setModelTokenUsage(modelTokenUsage);
        
        // 模型使用占比（按使用次数统计）
        Map<String, Long> modelDistribution = new HashMap<>();
        
        // 统计每个模型的使用次数（消息数量）
        Map<Long, Long> modelUsageCountMap = new HashMap<>();
        for (ChatMessage msg : allMessages) {
            if (msg.getModelId() != null) {
                modelUsageCountMap.put(msg.getModelId(), 
                    modelUsageCountMap.getOrDefault(msg.getModelId(), 0L) + 1);
            }
        }
        
        // 转换为模型名称和次数的映射
        for (Map.Entry<Long, Long> entry : modelUsageCountMap.entrySet()) {
            Long modelId = entry.getKey();
            Long usageCount = entry.getValue();
            
            // 查找模型名称
            QAModel model = allModels.stream()
                    .filter(m -> m.getId().equals(modelId))
                    .findFirst()
                    .orElse(null);
            
            String modelName = model != null ? model.getName() : "未知模型";
            modelDistribution.put(modelName, usageCount);
        }
        
        tokenStats.setModelDistribution(modelDistribution);
        
        // Token使用趋势（按模型分组，最近N天）
        List<StatisticsResponse.ModelDailyTokenCount> modelTokenTrend = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = Calendar.getInstance();
        
        // 获取所有有token信息的消息（包含modelId）
        List<ChatMessage> allMessagesWithTokens = chatMessageRepository.findAll().stream()
                .filter(m -> m.getTotalTokens() != null && m.getTotalTokens() > 0 && m.getModelId() != null)
                .collect(Collectors.toList());
        
        // 获取所有模型（用于查找模型名称）
        List<QAModel> allModelsForTrend = qaModelRepository.findAll().stream()
                .filter(m -> m.getDeleted() == null || m.getDeleted() == 0)
                .collect(Collectors.toList());
        
        // 获取有使用记录的模型ID列表
        Set<Long> usedModelIds = allMessagesWithTokens.stream()
                .map(ChatMessage::getModelId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        
        // 为每个日期和每个模型生成数据
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
            
            // 为每个模型统计当天的token使用量
            for (Long modelId : usedModelIds) {
                // 查找模型名称
                QAModel model = allModelsForTrend.stream()
                        .filter(m -> m.getId().equals(modelId))
                        .findFirst()
                        .orElse(null);
                
                String modelName = model != null ? model.getName() : "未知模型";
                
                // 统计该模型当天的token使用量
                long dayPromptTokens = allMessagesWithTokens.stream()
                        .filter(m -> m.getModelId() != null && m.getModelId().equals(modelId)
                                && m.getCreateTime() != null
                                && m.getCreateTime().after(startOfDay)
                                && m.getCreateTime().before(endOfDay)
                                && m.getPromptTokens() != null)
                        .mapToLong(m -> m.getPromptTokens())
                        .sum();
                
                long dayCompletionTokens = allMessagesWithTokens.stream()
                        .filter(m -> m.getModelId() != null && m.getModelId().equals(modelId)
                                && m.getCreateTime() != null
                                && m.getCreateTime().after(startOfDay)
                                && m.getCreateTime().before(endOfDay)
                                && m.getCompletionTokens() != null)
                        .mapToLong(m -> m.getCompletionTokens())
                        .sum();
                
                long dayTotalTokens = allMessagesWithTokens.stream()
                        .filter(m -> m.getModelId() != null && m.getModelId().equals(modelId)
                                && m.getCreateTime() != null
                                && m.getCreateTime().after(startOfDay)
                                && m.getCreateTime().before(endOfDay)
                                && m.getTotalTokens() != null)
                        .mapToLong(m -> m.getTotalTokens())
                        .sum();
                
                // 只添加有数据的记录
                if (dayTotalTokens > 0 || dayPromptTokens > 0 || dayCompletionTokens > 0) {
                    StatisticsResponse.ModelDailyTokenCount daily = new StatisticsResponse.ModelDailyTokenCount();
                    daily.setDate(dateStr);
                    daily.setModelId(modelId);
                    daily.setModelName(modelName);
                    daily.setPromptTokens(dayPromptTokens);
                    daily.setCompletionTokens(dayCompletionTokens);
                    daily.setTotalTokens(dayTotalTokens);
                    modelTokenTrend.add(daily);
                }
            }
        }
        
        tokenStats.setTokenTrend(modelTokenTrend);
        
        return tokenStats;
    }
    
    @Override
    public StatisticsResponse getAllStatistics() {
        return getAllStatistics(30); // 默认30天
    }
    
    @Override
    public StatisticsResponse getAllStatistics(Integer days) {
        StatisticsResponse response = new StatisticsResponse();
        response.setOverview(getOverviewStatistics());
        response.setUsers(getUserStatistics(days));
        response.setApps(getAppStatistics());
        response.setKnowledgeBases(getKnowledgeBaseStatistics());
        response.setModelTokens(getModelTokenStatistics(days));
        return response;
    }
}

