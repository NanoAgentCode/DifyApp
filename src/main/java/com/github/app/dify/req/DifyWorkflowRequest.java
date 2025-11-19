package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Map;

/**
 * Dify Workflow请求
 */
@ApiModel("Dify Workflow请求")
public class DifyWorkflowRequest {
    
    @ApiModelProperty("用户ID")
    private String userId;
    
    @ApiModelProperty("输入参数")
    private Map<String, Object> inputs;
    
    @ApiModelProperty("是否流式响应")
    private Boolean stream;
    
    // Getters and Setters
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

