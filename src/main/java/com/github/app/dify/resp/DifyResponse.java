package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Map;

/**
 * Dify响应
 */
@ApiModel("Dify响应")
public class DifyResponse {
    
    @ApiModelProperty("事件类型")
    private String event;
    
    @ApiModelProperty("任务ID")
    private String taskId;
    
    @ApiModelProperty("消息ID")
    private String messageId;
    
    @ApiModelProperty("会话ID")
    private String conversationId;
    
    @ApiModelProperty("答案")
    private String answer;
    
    @ApiModelProperty("创建时间")
    private Long createdAt;
    
    @ApiModelProperty("元数据")
    private Map<String, Object> metadata;
    
    @ApiModelProperty("是否完成")
    private Boolean finished;
    
    // Getters and Setters
    public String getEvent() {
        return event;
    }
    
    public void setEvent(String event) {
        this.event = event;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public Long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    public Boolean getFinished() {
        return finished;
    }
    
    public void setFinished(Boolean finished) {
        this.finished = finished;
    }
}

