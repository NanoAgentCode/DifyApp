package com.github.app.dify.analytics.statistics.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 统计响应基类
 */
@Schema(description = "统计响应")
public class StatisticsResponse {
    
    @Schema(description = "概览统计")
    private OverviewStatistics overview;
    
    @Schema(description = "用户统计")
    private UserStatistics users;
    
    @Schema(description = "应用统计")
    private AppStatistics apps;
    
    @Schema(description = "知识库统计")
    private KnowledgeBaseStatistics knowledgeBases;
    
    @Schema(description = "模型Token统计")
    private ModelTokenStatistics modelTokens;

    public OverviewStatistics getOverview() {
        return overview;
    }

    public void setOverview(OverviewStatistics overview) {
        this.overview = overview;
    }

    public UserStatistics getUsers() {
        return users;
    }

    public void setUsers(UserStatistics users) {
        this.users = users;
    }

    public AppStatistics getApps() {
        return apps;
    }

    public void setApps(AppStatistics apps) {
        this.apps = apps;
    }

    public KnowledgeBaseStatistics getKnowledgeBases() {
        return knowledgeBases;
    }

    public void setKnowledgeBases(KnowledgeBaseStatistics knowledgeBases) {
        this.knowledgeBases = knowledgeBases;
    }

    public ModelTokenStatistics getModelTokens() {
        return modelTokens;
    }

    public void setModelTokens(ModelTokenStatistics modelTokens) {
        this.modelTokens = modelTokens;
    }

    /**
     * 概览统计
     */
    @Schema(description = "概览统计")
    public static class OverviewStatistics {
        @Schema(description = "用户总数")
        private Long totalUsers;
        
        @Schema(description = "应用总数")
        private Long totalApps;
        
        @Schema(description = "知识库总数")
        private Long totalKnowledgeBases;
        
        @Schema(description = "会话总数")
        private Long totalConversations;
        
        @Schema(description = "消息总数")
        private Long totalMessages;

        public Long getTotalUsers() {
            return totalUsers;
        }

        public void setTotalUsers(Long totalUsers) {
            this.totalUsers = totalUsers;
        }

        public Long getTotalApps() {
            return totalApps;
        }

        public void setTotalApps(Long totalApps) {
            this.totalApps = totalApps;
        }

        public Long getTotalKnowledgeBases() {
            return totalKnowledgeBases;
        }

        public void setTotalKnowledgeBases(Long totalKnowledgeBases) {
            this.totalKnowledgeBases = totalKnowledgeBases;
        }

        public Long getTotalConversations() {
            return totalConversations;
        }

        public void setTotalConversations(Long totalConversations) {
            this.totalConversations = totalConversations;
        }

        public Long getTotalMessages() {
            return totalMessages;
        }

        public void setTotalMessages(Long totalMessages) {
            this.totalMessages = totalMessages;
        }
    }

    /**
     * 用户统计
     */
    @Schema(description = "用户统计")
    public static class UserStatistics {
        @Schema(description = "用户总数")
        private Long total;
        
        @Schema(description = "角色分布")
        private Map<String, Long> roleDistribution;
        
        @Schema(description = "状态分布")
        private Map<String, Long> statusDistribution;
        
        @Schema(description = "注册趋势（按天）")
        private List<DailyCount> registrationTrend;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Map<String, Long> getRoleDistribution() {
            return roleDistribution;
        }

        public void setRoleDistribution(Map<String, Long> roleDistribution) {
            this.roleDistribution = roleDistribution;
        }

        public Map<String, Long> getStatusDistribution() {
            return statusDistribution;
        }

        public void setStatusDistribution(Map<String, Long> statusDistribution) {
            this.statusDistribution = statusDistribution;
        }

        public List<DailyCount> getRegistrationTrend() {
            return registrationTrend;
        }

        public void setRegistrationTrend(List<DailyCount> registrationTrend) {
            this.registrationTrend = registrationTrend;
        }
    }

    /**
     * 应用统计
     */
    @Schema(description = "应用统计")
    public static class AppStatistics {
        @Schema(description = "应用总数")
        private Long total;
        
        @Schema(description = "类型分布")
        private Map<String, Long> typeDistribution;
        
        @Schema(description = "应用使用情况（按应用分组统计会话数）")
        private List<AppUsage> appUsage;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Map<String, Long> getTypeDistribution() {
            return typeDistribution;
        }

        public void setTypeDistribution(Map<String, Long> typeDistribution) {
            this.typeDistribution = typeDistribution;
        }

        public List<AppUsage> getAppUsage() {
            return appUsage;
        }

        public void setAppUsage(List<AppUsage> appUsage) {
            this.appUsage = appUsage;
        }
    }

    /**
     * 知识库统计
     */
    @Schema(description = "知识库统计")
    public static class KnowledgeBaseStatistics {
        @Schema(description = "知识库总数")
        private Long total;
        
        @Schema(description = "状态分布")
        private Map<String, Long> statusDistribution;
        
        @Schema(description = "知识库使用情况（按知识库分组统计会话数）")
        private List<KnowledgeBaseUsage> kbUsage;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }

        public Map<String, Long> getStatusDistribution() {
            return statusDistribution;
        }

        public void setStatusDistribution(Map<String, Long> statusDistribution) {
            this.statusDistribution = statusDistribution;
        }

        public List<KnowledgeBaseUsage> getKbUsage() {
            return kbUsage;
        }

        public void setKbUsage(List<KnowledgeBaseUsage> kbUsage) {
            this.kbUsage = kbUsage;
        }
    }

    /**
     * 模型Token统计
     */
    @Schema(description = "模型Token统计")
    public static class ModelTokenStatistics {
        @Schema(description = "总Token数")
        private Long totalTokens;
        
        @Schema(description = "各模型Token使用量")
        private List<ModelTokenUsage> modelTokenUsage;
        
        @Schema(description = "Token使用趋势（按天，按模型分组）")
        private List<ModelDailyTokenCount> tokenTrend;
        
        @Schema(description = "模型使用占比")
        private Map<String, Long> modelDistribution;

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public List<ModelTokenUsage> getModelTokenUsage() {
            return modelTokenUsage;
        }

        public void setModelTokenUsage(List<ModelTokenUsage> modelTokenUsage) {
            this.modelTokenUsage = modelTokenUsage;
        }

        public List<ModelDailyTokenCount> getTokenTrend() {
            return tokenTrend;
        }

        public void setTokenTrend(List<ModelDailyTokenCount> tokenTrend) {
            this.tokenTrend = tokenTrend;
        }

        public Map<String, Long> getModelDistribution() {
            return modelDistribution;
        }

        public void setModelDistribution(Map<String, Long> modelDistribution) {
            this.modelDistribution = modelDistribution;
        }
    }

    /**
     * 每日计数
     */
    @Schema(description = "每日计数")
    public static class DailyCount {
        @Schema(description = "日期")
        private String date;
        
        @Schema(description = "数量")
        private Long count;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 应用使用情况
     */
    @Schema(description = "应用使用情况")
    public static class AppUsage {
        @Schema(description = "应用ID")
        private Long appId;
        
        @Schema(description = "应用名称")
        private String appName;
        
        @Schema(description = "会话数")
        private Long conversationCount;

        public Long getAppId() {
            return appId;
        }

        public void setAppId(Long appId) {
            this.appId = appId;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public Long getConversationCount() {
            return conversationCount;
        }

        public void setConversationCount(Long conversationCount) {
            this.conversationCount = conversationCount;
        }
    }

    /**
     * 知识库使用情况
     */
    @Schema(description = "知识库使用情况")
    public static class KnowledgeBaseUsage {
        @Schema(description = "知识库ID")
        private Long kbId;
        
        @Schema(description = "知识库名称")
        private String kbName;
        
        @Schema(description = "会话数")
        private Long conversationCount;

        public Long getKbId() {
            return kbId;
        }

        public void setKbId(Long kbId) {
            this.kbId = kbId;
        }

        public String getKbName() {
            return kbName;
        }

        public void setKbName(String kbName) {
            this.kbName = kbName;
        }

        public Long getConversationCount() {
            return conversationCount;
        }

        public void setConversationCount(Long conversationCount) {
            this.conversationCount = conversationCount;
        }
    }

    /**
     * 模型Token使用情况
     */
    @Schema(description = "模型Token使用情况")
    public static class ModelTokenUsage {
        @Schema(description = "模型ID")
        private Long modelId;
        
        @Schema(description = "模型名称")
        private String modelName;
        
        @Schema(description = "Token总数")
        private Long totalTokens;
        
        @Schema(description = "Prompt Tokens")
        private Long promptTokens;
        
        @Schema(description = "Completion Tokens")
        private Long completionTokens;

        public Long getModelId() {
            return modelId;
        }

        public void setModelId(Long modelId) {
            this.modelId = modelId;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Long promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Long completionTokens) {
            this.completionTokens = completionTokens;
        }
    }

    /**
     * 每日Token计数
     */
    @Schema(description = "每日Token计数")
    public static class DailyTokenCount {
        @Schema(description = "日期")
        private String date;
        
        @Schema(description = "Token总数")
        private Long totalTokens;
        
        @Schema(description = "Prompt Tokens")
        private Long promptTokens;
        
        @Schema(description = "Completion Tokens")
        private Long completionTokens;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Long promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Long completionTokens) {
            this.completionTokens = completionTokens;
        }
    }

    /**
     * 按模型分组的每日Token计数
     */
    @Schema(description = "按模型分组的每日Token计数")
    public static class ModelDailyTokenCount {
        @Schema(description = "日期")
        private String date;
        
        @Schema(description = "模型ID")
        private Long modelId;
        
        @Schema(description = "模型名称")
        private String modelName;
        
        @Schema(description = "Token总数")
        private Long totalTokens;
        
        @Schema(description = "Prompt Tokens")
        private Long promptTokens;
        
        @Schema(description = "Completion Tokens")
        private Long completionTokens;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getModelId() {
            return modelId;
        }

        public void setModelId(Long modelId) {
            this.modelId = modelId;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Long promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Long completionTokens) {
            this.completionTokens = completionTokens;
        }
    }
}

