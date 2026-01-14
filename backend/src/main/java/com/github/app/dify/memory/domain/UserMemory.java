package com.github.app.dify.memory.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Date;

@Entity
@Table(
        name = "USER_MEMORY",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "scope_type", "scope_id", "memory_type", "memory_key"})
)
public class UserMemory implements Serializable {

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

    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;

    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;

    @Schema(description = "是否删除：0-未删除，1-已删除")
    @Column(name = "deleted")
    private Integer deleted;

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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

