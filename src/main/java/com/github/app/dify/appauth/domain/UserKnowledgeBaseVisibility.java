package com.github.app.dify.appauth.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
/**
 * 用户知识库可见性表
 * @TableName USER_KNOWLEDGE_BASE_VISIBILITY
 */
@Entity
@Table(name = "USER_KNOWLEDGE_BASE_VISIBILITY", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "knowledge_base_id"})
})
public class UserKnowledgeBaseVisibility implements Serializable {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * 知识库ID
     */
    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;
    
    /**
     * 是否可见：true-可见，false-不可见
     */
    @Column(name = "visible", nullable = false)
    private Boolean visible;
    
    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private Date updateTime;

    // Getters and Setters
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

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
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
}

