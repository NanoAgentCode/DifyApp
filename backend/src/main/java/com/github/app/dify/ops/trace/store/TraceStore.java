package com.github.app.dify.ops.trace.store;

import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.ops.trace.model.TraceStep;

/**
 * 追踪存储端口。
 * 对上层屏蔽具体存储（ES/DB/OTEL）。
 */
public interface TraceStore {

    TraceHandle start(TraceStartRequest request);

    void end(TraceHandle handle, String responseContent, Integer inputTokens, Integer outputTokens, Integer totalTokens);

    void error(TraceHandle handle, String errorContent, long latency);

    void appendStep(TraceHandle handle, TraceStep step);
}

