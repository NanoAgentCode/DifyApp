package com.github.app.dify.observability.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.observability.config.ObservabilityConfig;
import com.github.app.dify.observability.domain.LLMTrace;
import com.github.app.dify.observability.repository.LLMTraceRepository;
import com.github.app.dify.observability.service.LLMTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LLM追踪服务实现
 * 优化点：
 * 1. 数据库操作异步化，不阻塞主业务流程
 * 2. 异常隔离，监控失败不影响业务
 * 3. 支持批量处理
 */
@Service
public class LLMTraceServiceImpl implements LLMTraceService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LLMTraceServiceImpl.class);

    @Autowired
    private LLMTraceRepository traceRepository;

    @Autowired(required = false)
    private ObservabilityConfig observabilityConfig;

    @Autowired(required = false)
    @Qualifier("observabilityExecutor")
    private java.util.concurrent.Executor observabilityExecutor;

    @Override
    @Transactional
    public LLMTrace saveTrace(LLMTrace trace) {
        return traceRepository.save(trace);
    }

    @Override
    public Page<LLMTrace> listTraces(String model, String provider, String traceSource, String conversationId,
            LocalDateTime startTime,
            LocalDateTime endTime, Pageable pageable) {
        Specification<LLMTrace> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (model != null && !model.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("model")), "%" + model.toLowerCase() + "%"));
            }
            if (provider != null && !provider.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("provider")), "%" + provider.toLowerCase() + "%"));
            }
            if (traceSource != null && !traceSource.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("traceSource")), "%" + traceSource.toLowerCase() + "%"));
            }
            if (conversationId != null && !conversationId.isEmpty()) {
                predicates.add(cb.like(root.get("conversationId"), "%" + conversationId + "%"));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return traceRepository.findAll(spec, pageable);
    }

    @Override
    public LLMTrace getTrace(Long id) {
        return traceRepository.findById(id).orElseThrow(() -> new NotFoundException("Trace not found"));
    }

    @Override
    public void recordStart(String traceId, String model, String provider, String conversationId,
            String requestContent, String traceSource) {
        // 异步执行，不阻塞调用线程
        recordStartAsync(traceId, model, provider, conversationId, requestContent, traceSource);
    }

    /**
     * 异步记录追踪开始（内部方法）
     */
    @Async("observabilityExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> recordStartAsync(String traceId, String model, String provider, String conversationId,
            String requestContent, String traceSource) {
        // 检查是否启用监控
        if (observabilityConfig != null && !observabilityConfig.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Recording trace start, traceId: {}, model: {}", traceId, model);
            }
            
            LLMTrace trace = new LLMTrace();
            trace.setTraceId(traceId);
            trace.setModel(model);
            trace.setProvider(provider);
            trace.setConversationId(conversationId);
            trace.setRequestContent(requestContent);
            trace.setTraceSource(traceSource);
            trace.setCreatedAt(LocalDateTime.now());
            trace.setStatus(0); // Default to failed/in-progress, update on end
            
            traceRepository.saveAndFlush(trace);
            
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            // 异常隔离：监控失败不影响业务
            logger.warn("记录trace start失败，traceId: {}, 错误: {}", traceId, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void recordEnd(String traceId, String responseContent, Integer inputTokens, Integer outputTokens,
            Integer totalTokens) {
        // 异步执行，不阻塞调用线程
        recordEndAsync(traceId, responseContent, inputTokens, outputTokens, totalTokens);
    }

    /**
     * 异步记录追踪结束（内部方法）
     */
    @Async("observabilityExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> recordEndAsync(String traceId, String responseContent, Integer inputTokens, Integer outputTokens,
            Integer totalTokens) {
        // 检查是否启用监控
        if (observabilityConfig != null && !observabilityConfig.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Recording trace end, traceId: {}", traceId);
            }
            
            LLMTrace trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                logger.warn("Trace record not found in recordEnd for traceId: {}", traceId);
                trace = new LLMTrace();
                trace.setTraceId(traceId);
                trace.setCreatedAt(LocalDateTime.now());
            }

            trace.setFinishedAt(LocalDateTime.now());
            if (trace.getCreatedAt() != null) {
                long latency = java.time.Duration.between(trace.getCreatedAt(), trace.getFinishedAt()).toMillis();
                trace.setLatency(latency);
            }

            trace.setResponseContent(responseContent);
            trace.setInputTokens(inputTokens);
            trace.setOutputTokens(outputTokens);
            trace.setTotalTokens(totalTokens);
            trace.setStatus(1); // Success

            traceRepository.saveAndFlush(trace);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            // 异常隔离：监控失败不影响业务
            logger.warn("记录LLM追踪结束失败，traceId: {}", traceId, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public void recordError(String traceId, String errorContent, long latency) {
        // 异步执行，不阻塞调用线程
        recordErrorAsync(traceId, errorContent, latency);
    }

    /**
     * 异步记录追踪错误（内部方法）
     */
    @Async("observabilityExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Void> recordErrorAsync(String traceId, String errorContent, long latency) {
        // 检查是否启用监控
        if (observabilityConfig != null && !observabilityConfig.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Recording trace error, traceId: {}", traceId);
            }
            
            LLMTrace trace = traceRepository.findByTraceId(traceId).orElse(null);
            if (trace == null) {
                trace = new LLMTrace();
                trace.setTraceId(traceId);
                trace.setCreatedAt(LocalDateTime.now());
            }

            trace.setFinishedAt(LocalDateTime.now());
            if (trace.getLatency() == null || trace.getLatency() == 0) {
                trace.setLatency(latency > 0 ? latency : 0);
            }
            trace.setErrorContent(errorContent);
            trace.setStatus(0); // Failed

            traceRepository.saveAndFlush(trace);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            // 异常隔离：监控失败不影响业务
            logger.warn("记录LLM追踪错误失败，traceId: {}", traceId, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    @Transactional
    public void deleteTrace(Long id) {
        if (!traceRepository.existsById(id)) {
            throw new NotFoundException("Trace not found");
        }
        traceRepository.deleteById(id);
    }
}
