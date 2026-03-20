package com.github.app.dify.ops.trace.api.impl;

import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.core.TraceSanitizer;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.ops.trace.model.TraceStep;
import com.github.app.dify.ops.trace.store.TraceStore;
import org.springframework.stereotype.Component;

/**
 * 默认业务追踪门面实现。
 */
@Component
public class DefaultTraceFacade implements TraceFacade {

    private final TraceStore traceStore;
    private final TraceSanitizer sanitizer;

    public DefaultTraceFacade(TraceStore traceStore, TraceSanitizer sanitizer) {
        this.traceStore = traceStore;
        this.sanitizer = sanitizer;
    }

    @Override
    public TraceHandle start(TraceStartRequest request) {
        return traceStore.start(request);
    }

    @Override
    public void step(TraceHandle handle, TraceStep step) {
        if (handle == null || step == null) {
            return;
        }
        step.setInputSummary(sanitizer.summarize(step.getInputSummary()));
        step.setOutputSummary(sanitizer.summarize(step.getOutputSummary()));
        step.setErrorSummary(sanitizer.summarize(step.getErrorSummary()));
        traceStore.appendStep(handle, step);
    }

    @Override
    public void success(TraceHandle handle, String responseSummary) {
        success(handle, responseSummary, null, null, null);
    }

    @Override
    public void success(TraceHandle handle, String responseSummary, Integer inputTokens, Integer outputTokens,
            Integer totalTokens) {
        traceStore.end(handle, sanitizer.summarize(responseSummary), inputTokens, outputTokens, totalTokens);
    }

    @Override
    public void error(TraceHandle handle, Throwable error) {
        traceStore.error(handle, error == null ? "unknown error" : sanitizer.summarize(error.getMessage()), 0);
    }
}

