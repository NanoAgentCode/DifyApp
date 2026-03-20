package com.github.app.dify.ops.trace.store.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 追踪存储记录（与具体存储实现解耦）。
 */
@Data
public class TraceRecord {

    private String id;
    private String traceId;
    private String spanId;
    private String conversationId;
    private String model;
    private String provider;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Long latency;
    private Integer status;
    private String requestContent;
    private String responseContent;
    private String errorContent;
    private String metaData;
    private String traceSource;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}

