package com.github.app.dify.chat.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 会话消息表（会话中的单条消息，一问一答为一轮对话）
 * @TableName chat_message
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage implements Serializable {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "主键")
    private Long id;

    /**
     * 会话ID（外键关联chat_conversation）
     */
    @Schema(description = "会话ID")
    @Column(name = "conversation_id")
    private Long conversationId;

    /**
     * 角色（user/assistant）
     */
    @Schema(description = "角色（user/assistant）")
    @Column(name = "role", length = 20)
    private String role;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 消息顺序
     */
    @Schema(description = "消息顺序")
    @Column(name = "sequence")
    private Integer sequence;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}