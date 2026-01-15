package com.github.app.dify.chat.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话消息表（会话中的单条消息，一问一答为一轮对话）
 * @TableName chat_message
 */
@Setter
@Getter
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

}
