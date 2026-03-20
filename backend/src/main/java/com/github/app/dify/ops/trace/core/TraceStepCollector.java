package com.github.app.dify.ops.trace.core;

import com.github.app.dify.ops.trace.api.TraceFacade;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStep;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 步骤采集器：统一记录步骤输入/输出/耗时/异常。
 */
public class TraceStepCollector {

    private final TraceFacade traceFacade;
    private final TraceHandle handle;
    private final TraceSanitizer sanitizer;

    public TraceStepCollector(TraceFacade traceFacade, TraceHandle handle, TraceSanitizer sanitizer) {
        this.traceFacade = traceFacade;
        this.handle = handle;
        this.sanitizer = sanitizer;
    }

    public <T> T trace(String stepCode, String stepName, Object input, Supplier<T> action, Function<T, Object> outputMapper) {
        LocalDateTime start = LocalDateTime.now();
        try {
            T result = action.get();
            LocalDateTime end = LocalDateTime.now();
            TraceStep step = new TraceStep();
            step.setStepCode(stepCode);
            step.setStepName(stepName);
            step.setStatus("SUCCESS");
            step.setStartAt(start);
            step.setEndAt(end);
            step.setDurationMs(Duration.between(start, end).toMillis());
            step.setInputSummary(sanitizer.summarize(input));
            step.setOutputSummary(sanitizer.summarize(outputMapper.apply(result)));
            traceFacade.step(handle, step);
            return result;
        } catch (RuntimeException ex) {
            LocalDateTime end = LocalDateTime.now();
            TraceStep step = new TraceStep();
            step.setStepCode(stepCode);
            step.setStepName(stepName);
            step.setStatus("FAILED");
            step.setStartAt(start);
            step.setEndAt(end);
            step.setDurationMs(Duration.between(start, end).toMillis());
            step.setInputSummary(sanitizer.summarize(input));
            step.setErrorSummary(sanitizer.summarize(ex.getMessage()));
            traceFacade.step(handle, step);
            throw ex;
        }
    }
}

