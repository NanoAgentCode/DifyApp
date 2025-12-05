package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 智能问答请求（直接对话，不使用知识库）
 */
@ApiModel("智能问答请求")
public class ChatRequest {
    
    @NotBlank(message = "问题不能为空")
    @ApiModelProperty(value = "问题", required = true)
    private String question;
    
    @ApiModelProperty("对话ID（用于多轮对话）")
    private String conversationId;
    
    @ApiModelProperty("用户ID")
    private String userId;
    
    @ApiModelProperty("对话历史")
    private List<Message> history;
    
    @ApiModelProperty("是否流式响应")
    private Boolean stream;
    
    @ApiModelProperty("模型ID（从数据库中选择的问答模型）")
    private Long modelId;
    
    @ApiModelProperty("是否启用浏览器检索（MCP协议）")
    private Boolean enableBrowserSearch;
    
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
    
    public Boolean getEnableBrowserSearch() {
        return enableBrowserSearch;
    }
    
    public void setEnableBrowserSearch(Boolean enableBrowserSearch) {
        this.enableBrowserSearch = enableBrowserSearch;
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

