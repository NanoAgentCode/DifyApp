package com.github.app.dify.ops.trace.model;

import lombok.Data;

/**
 * 启动业务追踪的请求参数。
 */
@Data
public class TraceStartRequest {

    private String traceSource;

    private String conversationId;

    private Long userId;

    private String requestType;

    private Long businessId;

    private String requestSummary;

    /**
     * 模型名称（LLM调用场景可填）。
     */
    private String model;

    /**
     * 模型提供商（LLM调用场景可填）。
     */
    private String provider;
}

