package com.github.app.dify.observability.service;

import com.github.app.dify.observability.domain.LLMTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface LLMTraceService {

        LLMTrace saveTrace(LLMTrace trace);

        Page<LLMTrace> listTraces(String model, String provider, String traceSource, String conversationId,
                        LocalDateTime startTime,
                        LocalDateTime endTime, Pageable pageable);

        LLMTrace getTrace(Long id);

        void recordStart(String traceId, String model, String provider, String conversationId, String requestContent,
                        String traceSource);

        void recordEnd(String traceId, String responseContent, Integer inputTokens, Integer outputTokens,
                        Integer totalTokens);

        void recordError(String traceId, String errorContent, long latency);

        void deleteTrace(Long id);
}
