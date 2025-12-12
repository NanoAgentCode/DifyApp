package com.github.app.dify.chat.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
/**
 * Dify API响应对象
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DifyResponse {
    
    /**
     * 事件类型（如：message, workflow_started等）
     */
    private String event;
    
    /**
     * 回答内容
     */
    private String answer;
    
    /**
     * 是否完成
     */
    private Boolean finished;
    
    /**
     * 对话ID
     */
    @JsonProperty("conversation_id")
    private String conversationId;
    
    /**
     * 消息ID
     */
    @JsonProperty("message_id")
    private String messageId;
    
    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    private Long createdAt;
    
    /**
     * 任务ID（Workflow使用）
     */
    @JsonProperty("task_id")
    private String taskId;
    
    /**
     * ID（可能与message_id相同）
     */
    private String id;
    
    /**
     * 来源变量选择器
     */
    @JsonProperty("from_variable_selector")
    private List<String> fromVariableSelector;
    
    /**
     * 工作流运行ID
     */
    @JsonProperty("workflow_run_id")
    private String workflowRunId;
    
    /**
     * 数据（Workflow使用）
     */
    private Object data;
    
    /**
     * 元数据
     */
    private Object metadata;
    
    public String getEvent() {
        return event;
    }
    
    public void setEvent(String event) {
        this.event = event;
    }
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public Boolean getFinished() {
        return finished;
    }
    
    public void setFinished(Boolean finished) {
        this.finished = finished;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public Long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getWorkflowRunId() {
        return workflowRunId;
    }
    
    public void setWorkflowRunId(String workflowRunId) {
        this.workflowRunId = workflowRunId;
    }
    
    public Object getData() {
        return data;
    }
    
    public void setData(Object data) {
        this.data = data;
    }
    
    public Object getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public List<String> getFromVariableSelector() {
        return fromVariableSelector;
    }
    
    public void setFromVariableSelector(List<String> fromVariableSelector) {
        this.fromVariableSelector = fromVariableSelector;
    }
    
    @Override
    public String toString() {
        return "DifyResponse{" +
                "event='" + event + '\'' +
                ", answer='" + answer + '\'' +
                ", finished=" + finished +
                ", conversationId='" + conversationId + '\'' +
                ", messageId='" + messageId + '\'' +
                ", createdAt=" + createdAt +
                ", taskId='" + taskId + '\'' +
                ", id='" + id + '\'' +
                ", workflowRunId='" + workflowRunId + '\'' +
                '}';
    }
}