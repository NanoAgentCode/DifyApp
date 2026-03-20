package com.github.app.dify.ops.trace.store;

import com.github.app.dify.ops.trace.store.model.TraceRecord;

/**
 * 追踪文档网关，屏蔽底层ES实现。
 */
public interface TraceDocumentGateway {

    void saveWithId(String id, TraceRecord record);

    TraceRecord getById(String id);

    TraceRecord getByTraceId(String traceId);

    void update(String id, TraceRecord record);
}

