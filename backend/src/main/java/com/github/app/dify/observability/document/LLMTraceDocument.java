package com.github.app.dify.observability.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Elasticsearch LLM追踪文档
 * 
 * ID 设计：
 * - id: ES 文档 ID，等于 spanId
 * - traceId: 请求级链路 ID，同一请求的多次 LLM 调用共享此 ID
 * - spanId: 调用级 ID，每次 LLM 调用唯一
 */
@Data
public class LLMTraceDocument {

    /**
     * ES 文档 ID（不存储在文档内容中，仅用于查询）
     */
    @JsonIgnore
    private String id;

    /**
     * 请求级链路ID（同一请求的多次LLM调用共享）
     */
    private String traceId;

    /**
     * 调用级ID（每次LLM调用唯一，等于ES文档ID）
     */
    private String spanId;

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
