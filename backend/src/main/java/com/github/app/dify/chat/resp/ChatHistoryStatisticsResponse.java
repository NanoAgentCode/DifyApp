package com.github.app.dify.chat.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
/**
 * 统计响应
 */
@Schema(description = "统计响应")
public class ChatHistoryStatisticsResponse {
    
    @Schema(description = "总对话数")
    private Long totalConversations;
    
    @Schema(description = "总消息数")
    private Long totalMessages;
    
    @Schema(description = "用户对话数排行")
    private List<UserConversationRank> userConversationRanks;
    
    @Schema(description = "热门问题统计")
    private List<PopularQuestion> popularQuestions;
    
    @Schema(description = "对话类型分布")
    private Map<String, Long> typeDistribution;
    
    @Schema(description = "时间趋势（按天统计）")
    private List<DailyStatistics> dailyStatistics;

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

    public List<UserConversationRank> getUserConversationRanks() {
        return userConversationRanks;
    }

    public void setUserConversationRanks(List<UserConversationRank> userConversationRanks) {
        this.userConversationRanks = userConversationRanks;
    }

    public List<PopularQuestion> getPopularQuestions() {
        return popularQuestions;
    }

    public void setPopularQuestions(List<PopularQuestion> popularQuestions) {
        this.popularQuestions = popularQuestions;
    }

    public Map<String, Long> getTypeDistribution() {
        return typeDistribution;
    }

    public void setTypeDistribution(Map<String, Long> typeDistribution) {
        this.typeDistribution = typeDistribution;
    }

    public List<DailyStatistics> getDailyStatistics() {
        return dailyStatistics;
    }

    public void setDailyStatistics(List<DailyStatistics> dailyStatistics) {
        this.dailyStatistics = dailyStatistics;
    }

    /**
     * 用户对话数排行
     */
    @Schema(description = "用户对话数排行")
    public static class UserConversationRank {
        @Schema(description = "用户ID")
        private Long userId;
        
        @Schema(description = "用户名")
        private String username;
        
        @Schema(description = "对话数")
        private Long conversationCount;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Long getConversationCount() {
            return conversationCount;
        }

        public void setConversationCount(Long conversationCount) {
            this.conversationCount = conversationCount;
        }
    }

    /**
     * 热门问题
     */
    @Schema(description = "热门问题")
    public static class PopularQuestion {
        @Schema(description = "问题内容")
        private String question;
        
        @Schema(description = "出现次数")
        private Long count;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }

    /**
     * 每日统计
     */
    @Schema(description = "每日统计")
    public static class DailyStatistics {
        @Schema(description = "日期")
        private String date;
        
        @Schema(description = "对话数")
        private Long conversationCount;
        
        @Schema(description = "消息数")
        private Long messageCount;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getConversationCount() {
            return conversationCount;
        }

        public void setConversationCount(Long conversationCount) {
            this.conversationCount = conversationCount;
        }

        public Long getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(Long messageCount) {
            this.messageCount = messageCount;
        }
    }
}