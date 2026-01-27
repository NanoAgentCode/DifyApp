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

        /**
         * 记录追踪开始（异步，不阻塞）
         * 注意：方法返回void，但内部使用异步执行，不会阻塞调用线程
         */
        void recordStart(String traceId, String model, String provider, String conversationId, String requestContent,
                        String traceSource);

        /**
         * 记录追踪结束（异步，不阻塞）
         * 注意：方法返回void，但内部使用异步执行，不会阻塞调用线程
         */
        void recordEnd(String traceId, String responseContent, Integer inputTokens, Integer outputTokens,
                        Integer totalTokens);

        /**
         * 记录追踪错误（异步，不阻塞）
         * 注意：方法返回void，但内部使用异步执行，不会阻塞调用线程
         */
        void recordError(String traceId, String errorContent, long latency);

        void deleteTrace(Long id);
}
