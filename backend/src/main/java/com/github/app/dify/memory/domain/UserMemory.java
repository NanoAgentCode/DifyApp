package com.github.app.dify.memory.domain;

import com.github.app.dify.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(
        name = "USER_MEMORY",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "scope_type", "scope_id", "memory_type", "memory_key"})
)
public class UserMemory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户ID")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Schema(description = "作用域类型：chat/knowledge_base/app")
    @Column(name = "scope_type", length = 32, nullable = false)
    private String scopeType;

    @Schema(description = "作用域ID（知识库/应用ID，chat为空）")
    @Column(name = "scope_id")
    private Long scopeId;

    @Schema(description = "记忆类型：long_term/entity")
    @Column(name = "memory_type", length = 32, nullable = false)
    private String memoryType;

    @Schema(description = "记忆键（用于去重更新）")
    @Column(name = "memory_key", length = 200, nullable = false)
    private String memoryKey;

    @Schema(description = "记忆内容")
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Schema(description = "重要度（0-5）")
    @Column(name = "importance")
    private Integer importance;

    @Schema(description = "首次记录时间")
    @Column(name = "first_seen_time")
    private Date firstSeenTime;

    @Schema(description = "最近在对话中提及时间")
    @Column(name = "last_mentioned_time")
    private Date lastMentionedTime;

    @Schema(description = "最近被检索使用时间")
    @Column(name = "last_accessed_time")
    private Date lastAccessedTime;

    @Schema(description = "被检索使用次数")
    @Column(name = "access_count")
    private Integer accessCount;

    @Schema(description = "来源会话ID")
    @Column(name = "source_conversation_id")
    private Long sourceConversationId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
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

