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

    /**
     * 模型ID（关联使用的模型）
     */
    @Schema(description = "模型ID")
    @Column(name = "model_id")
    private Long modelId;

    /**
     * Prompt Tokens数量
     */
    @Schema(description = "Prompt Tokens数量")
    @Column(name = "prompt_tokens")
    private Long promptTokens;

    /**
     * Completion Tokens数量
     */
    @Schema(description = "Completion Tokens数量")
    @Column(name = "completion_tokens")
    private Long completionTokens;

    /**
     * 总Tokens数量
     */
    @Schema(description = "总Tokens数量")
    @Column(name = "total_tokens")
    private Long totalTokens;

    /**
     * 是否已删除（软删除标记）
     */
    @Schema(description = "是否已删除")
    @Column(name = "deleted")
    private Integer deleted;

    // ========== 数据库索引 ==========

    /**
     * 索引：会话ID - 用于查询某个会话的所有消息
     */
    @Index(name = "idx_conversation_id", columnList = "conversation_id")
    
    /**
     * 索引：会话ID + 消息顺序 - 用于按顺序查询会话消息
     */
    @Index(name = "idx_conversation_sequence", columnList = {"conversation_id", "sequence"})
    
    /**
     * 索引：创建时间 - 用于时间范围查询和统计
     */
    @Index(name = "idx_create_time", columnList = "create_time")
    
    /**
     * 索引：会话ID + 创建时间 - 用于复杂查询优化
     */
    @Index(name = "idx_conversation_create_time", columnList = {"conversation_id", "create_time"})
    
    /**
     * 索引：模型ID + 创建时间 - 用于模型使用统计
     */
    @Index(name = "idx_model_create_time", columnList = {"model_id", "create_time"})

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

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
