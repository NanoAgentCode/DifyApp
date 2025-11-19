package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Dify Chat请求
 */
@ApiModel("Dify Chat请求")
public class DifyChatRequest {
    
    @NotBlank(message = "查询内容不能为空")
    @ApiModelProperty(value = "查询内容", required = true)
    private String query;
    
    @ApiModelProperty("会话ID")
    private String conversationId;
    
    @ApiModelProperty("用户ID")
    private String userId;
    
    @ApiModelProperty("输入参数")
    private Map<String, Object> inputs;
    
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
    
    public Boolean getStream() {
        return stream;
    }
    
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
}

