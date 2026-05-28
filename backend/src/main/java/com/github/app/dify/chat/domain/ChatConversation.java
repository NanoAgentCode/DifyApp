package com.github.app.dify.chat.domain;

import com.github.app.dify.common.domain.BaseEntity;
import jakarta.persistence.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话表（一个会话包含多轮对话）
 * @TableName chat_conversation
 */
@Getter
@Setter
@Entity
@Table(name = "chat_conversation")
public class ChatConversation extends BaseEntity {

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
     * 对于文档问答类型（type=3），此字段存储文档ID
     */
    @Schema(description = "知识库ID（对于文档问答类型，此字段存储文档ID）")
    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    /**
     * 会话类型：1-普通聊天，2-知识库问答，3-文档问答，4-Agent任务，5-页面助手
     */
    @Schema(description = "会话类型：1-普通聊天，2-知识库问答，3-文档问答，4-Agent任务，5-页面助手")
    @Column(name = "type")
    private Integer type;

    /**
     * 会话标题（自动生成或用户自定义）
     */
    @Schema(description = "会话标题")
    @Column(name = "title", length = 500)
    private String title;

    /**
     * 模型ID（会话使用的模型，可选）
     */
    @Schema(description = "模型ID")
    @Column(name = "model_id")
    private Long modelId;

    /**
     * 会话滚动摘要（用于长会话上下文压缩）
     */
    @Schema(description = "会话滚动摘要")
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * 摘要已覆盖到的消息序号
     */
    @Schema(description = "摘要已覆盖到的消息序号")
    @Column(name = "summary_updated_sequence")
    private Integer summaryUpdatedSequence;

    /**
     * 摘要更新时间
     */
    @Schema(description = "摘要更新时间")
    @Column(name = "summary_update_time")
    private java.util.Date summaryUpdateTime;

    // ========== 数据库索引 ==========

}
