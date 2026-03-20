package com.github.app.dify.ops.trace.model;

import lombok.Data;

/**
 * 追踪句柄。
 * 对业务层屏蔽底层存储实现，只暴露追踪标识。
 */
@Data
public class TraceHandle {

    /**
     * 请求级链路ID。
     */
    private String traceId;

    /**
     * 调用级链路ID（通常等于ES文档ID）。
     */
    private String spanId;

    /**
     * 追踪来源。
     */
    private String traceSource;
}

