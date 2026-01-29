package com.github.app.dify.ops.observability.service;

import com.github.app.dify.ops.observability.domain.LLMTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * LLM追踪服务接口
 * 
 * ID 设计：
 * - traceId: 请求级链路ID，同一请求的多次LLM调用共享
 * - spanId: 调用级ID，每次LLM调用唯一（由 recordStart 返回）
 */
public interface LLMTraceService {

    LLMTrace saveTrace(LLMTrace trace);

    Page<LLMTrace> listTraces(String model, String provider, String traceSource, String conversationId,
                              LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    LLMTrace getTrace(Long id);

    void deleteTrace(Long id);

    /**
     * 记录追踪开始（异步）
     * 
     * @param traceId 请求级链路ID（同一请求多次调用共享）
     * @param model 模型名称
     * @param provider 供应商
     * @param conversationId 会话ID
     * @param requestContent 请求内容
     * @param traceSource 追踪来源
     * @return CompletableFuture<String> 返回 spanId（调用级ID），用于后续 recordEnd/recordError
     */
    CompletableFuture<String> recordStart(String traceId, String model, String provider, 
                                          String conversationId, String requestContent, String traceSource);

    /**
     * 记录追踪结束（异步）
     * 
     * @param id spanId 或 traceId（兼容模式：先按spanId查找，再按traceId查找）
     * @param responseContent 响应内容
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     * @param totalTokens 总 token 数
     */
    void recordEnd(String id, String responseContent, Integer inputTokens, 
                   Integer outputTokens, Integer totalTokens);

    /**
     * 记录追踪错误（异步）
     * 
     * @param id spanId 或 traceId（兼容模式：先按spanId查找，再按traceId查找）
     * @param errorContent 错误内容
     * @param latency 延迟（毫秒）
     */
    void recordError(String id, String errorContent, long latency);
}
