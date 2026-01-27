package com.github.app.dify.observability.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

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

    @Column(name = "trace_id", length = 64)
    private String traceId;

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

    // 1: success, 0: failed
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

    // Additional metadata stored as JSON string
    @Lob
    @Column(name = "meta_data", columnDefinition = "TEXT")
    private String metaData;

    @Column(name = "trace_source", length = 64)
    private String traceSource;
}
