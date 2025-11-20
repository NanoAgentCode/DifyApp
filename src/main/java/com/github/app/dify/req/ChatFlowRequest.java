package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * ChatFlow请求体（统一格式）
 */
@ApiModel("ChatFlow请求")
public class ChatFlowRequest {
    
    @NotBlank(message = "查询内容不能为空")
    @ApiModelProperty(value = "查询内容", required = true)
    private String query;
    
    @ApiModelProperty("会话ID")
    private String conversationId;
    
    @ApiModelProperty(value = "用户ID", required = true)
    private String userId;
    
    @ApiModelProperty("输入参数")
    private Map<String, Object> inputs;
    
    @ApiModelProperty("文件数组，用于文件上传场景")
    private java.util.List<Map<String, Object>> files;
    
    @ApiModelProperty("是否流式响应")
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

