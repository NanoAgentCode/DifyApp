package com.github.app.dify.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 会话表（一个会话包含多轮对话）
 * @TableName chat_conversation
 */
@Entity
@Table(name = "chat_conversation")
public class ChatConversation implements Serializable {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键")
    private Long id;

    /**
     * 用户ID（外键关联SYS_USER）
     */
    @Schema(description = "用户ID")
    @Column(name = "user_id")
    private Long userId;

    /**
     * 应用ID（可选，关联AI应用）
     */
    @Schema(description = "应用ID")
    @Column(name = "app_id")
    private Long appId;

    /**
     * 知识库ID（可选，关联知识库）
     */
    @Schema(description = "知识库ID")
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 会话类型：1-普通聊天，2-知识库问答
     */
    @Schema(description = "会话类型：1-普通聊天，2-知识库问答")
    @Column(name = "type")
    private Integer type;

    /**
     * 会话标题（自动生成或用户自定义）
     */
    @Schema(description = "会话标题")
    @Column(name = "title", length = 500)
    private String title;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;

    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @Schema(description = "是否删除")
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

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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