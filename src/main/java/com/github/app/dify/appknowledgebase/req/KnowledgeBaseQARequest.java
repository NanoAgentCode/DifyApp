package com.github.app.dify.appknowledgebase.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
/**
 * 知识库问答请求
 */
@Schema(description = "知识库问答请求")
public class KnowledgeBaseQARequest {
    
    @NotBlank(message = "问题不能为空")
    @Schema(description = "问题")
    private String question;
    
    @Schema(description = "对话ID（用于多轮对话）")
    private String conversationId;
    
    @Schema(description = "用户ID")
    private String userId;
    
    @Schema(description = "对话历史")
    private List<Message> history;
    
    @Schema(description = "是否流式响应")
    private Boolean stream;
    
    @Schema(description = "问答模型ID（可选，不指定则使用默认模型）")
    private Long modelId;
    
    public String getQuestion() {
        return question;
    }
    
    public void setQuestion(String question) {
        this.question = question;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public List<Message> getHistory() {
        return history;
    }
    
    public void setHistory(List<Message> history) {
        this.history = history;
    }
    
    public Boolean getStream() {
        return stream;
    }
    
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
    
    public Long getModelId() {
        return modelId;
    }
    
    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }
    
    /**
     * 消息
     */
    public static class Message {
        private String role; // user, assistant
        private String content;
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
    }
}