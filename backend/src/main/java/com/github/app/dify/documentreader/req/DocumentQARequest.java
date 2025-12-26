package com.github.app.dify.documentreader.req;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 文档问答请求
 */
@Schema(description = "文档问答请求")
public class DocumentQARequest {
    
    @Schema(description = "问题")
    private String question;
    
    @Schema(description = "会话ID")
    private Long conversationId;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "历史对话")
    private List<Map<String, String>> history;
    
    @Schema(description = "模型ID")
    private Long modelId;
    
    @Schema(description = "是否流式响应")
    private Boolean stream;

    // Getters and Setters
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<Map<String, String>> getHistory() {
        return history;
    }

    public void setHistory(List<Map<String, String>> history) {
        this.history = history;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}

