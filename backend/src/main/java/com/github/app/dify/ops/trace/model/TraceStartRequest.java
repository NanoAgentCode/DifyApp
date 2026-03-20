package com.github.app.dify.ops.trace.model;

import lombok.Data;

/**
 * 启动业务追踪的请求参数。
 */
@Data
public class TraceStartRequest {

    /**
     * 请求级链路ID（可选）。
     * 若传入则复用该 traceId；未传入则由存储层生成。
     */
    private String traceId;

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

