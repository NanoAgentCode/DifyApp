package com.github.app.dify.ops.trace.api;

import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.ops.trace.model.TraceStep;

/**
 * 业务追踪门面。
 * 业务模块只依赖该接口，避免与存储实现耦合。
 */
public interface TraceFacade {

    TraceHandle start(TraceStartRequest request);

    void step(TraceHandle handle, TraceStep step);

    void success(TraceHandle handle, String responseSummary);

    void success(TraceHandle handle, String responseSummary, Integer inputTokens, Integer outputTokens, Integer totalTokens);

    void error(TraceHandle handle, Throwable error);
}

