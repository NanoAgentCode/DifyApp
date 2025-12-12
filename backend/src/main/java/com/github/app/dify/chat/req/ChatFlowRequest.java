package com.github.app.dify.chat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
/**
 * ChatFlow请求体（统一格式）
 */
@Schema(description = "ChatFlow请求")
public class ChatFlowRequest {
    
    @NotBlank(message = "查询内容不能为空")
    @Schema(description = "查询内容")
    private String query;
    
    @Schema(description = "会话ID")
    private String conversationId;
    
    @Schema(description = "用户ID")
    private String userId;
    
    @Schema(description = "输入参数")
    private Map<String, Object> inputs;
    
    @Schema(description = "文件数组，用于文件上传场景")
    private java.util.List<Map<String, Object>> files;
    
    @Schema(description = "是否流式响应")
    private Boolean stream;
    
    // Getters and Setters
    public String getQuery() {
        return query;
    }
    
    public void setQuery(String query) {
        this.query = query;
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
    
    public Map<String, Object> getInputs() {
        return inputs;
    }
    
    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
    
    public java.util.List<Map<String, Object>> getFiles() {
        return files;
    }
    
    public void setFiles(java.util.List<Map<String, Object>> files) {
        this.files = files;
    }
    
    public Boolean getStream() {
        return stream;
    }
    
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}