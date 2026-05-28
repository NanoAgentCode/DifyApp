package com.github.app.dify.memory.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

@Schema(description = "用户记忆条目")
public class UserMemoryItemResp {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "作用域类型：chat/knowledge_base/app")
    private String scopeType;

    @Schema(description = "作用域ID（知识库/应用ID，chat为空）")
    private Long scopeId;

    @Schema(description = "记忆类型：long_term/entity")
    private String memoryType;

    @Schema(description = "记忆键")
    private String memoryKey;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "重要度（0-5）")
    private Integer importance;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "首次记录时间")
    private Date firstSeenTime;

    @Schema(description = "最近在对话中提及时间")
    private Date lastMentionedTime;

    @Schema(description = "最近被检索使用时间")
    private Date lastAccessedTime;

    @Schema(description = "被检索使用次数")
    private Integer accessCount;

    @Schema(description = "来源会话ID")
    private Long sourceConversationId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getImportance() {
        return importance;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Date getFirstSeenTime() {
        return firstSeenTime;
    }

    public void setFirstSeenTime(Date firstSeenTime) {
        this.firstSeenTime = firstSeenTime;
    }

    public Date getLastMentionedTime() {
        return lastMentionedTime;
    }

    public void setLastMentionedTime(Date lastMentionedTime) {
        this.lastMentionedTime = lastMentionedTime;
    }

    public Date getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(Date lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public Long getSourceConversationId() {
        return sourceConversationId;
    }

    public void setSourceConversationId(Long sourceConversationId) {
        this.sourceConversationId = sourceConversationId;
    }
}

