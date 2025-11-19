package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Map;

/**
 * Dify Workflow请求
 */
@ApiModel("Dify Workflow请求")
public class DifyWorkflowRequest {
    
    @ApiModelProperty(value = "用户ID", required = true, notes = "用于定义终端用户的身份，便于检索和统计")
    private String userId;
    
    @ApiModelProperty("输入参数，支持复杂结构（字符串、数组、对象等）")
    private Map<String, Object> inputs;
    
    @ApiModelProperty("是否流式响应")
    private Boolean stream;
    
    @ApiModelProperty("响应模式：streaming（流式，推荐）或 blocking（阻塞式）")
    private String responseMode;
    
    @ApiModelProperty("文件数组，用于文件上传场景")
    private List<Map<String, Object>> files;
    
    @ApiModelProperty("追踪ID，用于分布式追踪，可通过 Header (X-Trace-Id)、Query Parameter 或 Request Body 传递")
    private String traceId;
    
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
    
    public String getResponseMode() {
        return responseMode;
    }
    
    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }
    
    public List<Map<String, Object>> getFiles() {
        return files;
    }
    
    public void setFiles(List<Map<String, Object>> files) {
        this.files = files;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}

