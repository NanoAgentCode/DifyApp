package com.github.app.dify.observability.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Elasticsearch LLM追踪文档
 * 
 * 注意：id 字段存储 ES 文档的 _id，不会被序列化到 ES 文档内容中
 * traceId 是业务链路ID，会被存储在 ES 文档内容中
 */
@Data
public class LLMTraceDocument {

    /**
     * ES 文档 ID（不存储在文档内容中，仅用于查询）
     */
    @JsonIgnore
    private String id;

    /**
     * 业务链路ID（存储在文档内容中，用于关联同一请求的多个追踪）
     */
    private String traceId;

    private String conversationId;
    private String appName;
    private String model;
    private String provider;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Long latency;
    private Integer status;  // 1: success, 0: failed
    private String requestContent;
    private String responseContent;
    private String errorContent;
    private String metaData;
    private String traceSource;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime finishedAt;
}
