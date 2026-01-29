package com.github.app.dify.ops.observability.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * LLM追踪实体
 * 
 * 注意：此实体既用于 JPA 持久化（如果启用），也用于 ES 数据的返回
 * esDocId 是 ES 文档 ID，不持久化到数据库
 */
@Entity
@Table(name = "llm_trace", indexes = {
        @Index(name = "idx_trace_id", columnList = "trace_id")
})
@Data
@EntityListeners(AuditingEntityListener.class)
public class LLMTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 请求级链路ID（同一请求的多次LLM调用共享）
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    /**
     * 调用级ID（每次LLM调用唯一）
     */
    @Column(name = "span_id", length = 64)
    private String spanId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "app_name", length = 128)
    private String appName;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency")
    private Long latency;

    @Column(name = "status")
    private Integer status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Lob
    @Column(name = "request_content", columnDefinition = "TEXT")
    private String requestContent;

    @Lob
    @Column(name = "response_content", columnDefinition = "TEXT")
    private String responseContent;

    @Lob
    @Column(name = "error_content", columnDefinition = "TEXT")
    private String errorContent;

    @Lob
    @Column(name = "meta_data", columnDefinition = "TEXT")
    private String metaData;

    @Column(name = "trace_source", length = 64)
    private String traceSource;

    /**
     * ES 文档 ID（不持久化到数据库，用于前端查询/删除）
     */
    @Transient
    @JsonProperty("esDocId")
    private String esDocId;
}
