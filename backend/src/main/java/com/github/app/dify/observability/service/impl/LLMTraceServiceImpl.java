package com.github.app.dify.observability.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.observability.config.ObservabilityConfig;
import com.github.app.dify.observability.domain.LLMTrace;
import com.github.app.dify.observability.document.LLMTraceDocument;
import com.github.app.dify.observability.service.ElasticsearchObservabilityService;
import com.github.app.dify.observability.service.LLMTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * LLM追踪服务实现（仅Elasticsearch存储）
 * 
 * 简化设计：
 * 1. 所有数据仅存储在 ES 中
 * 2. 使用 ES 文档 ID 作为唯一标识
 * 3. 异步记录，不阻塞业务
 */
@Service
public class LLMTraceServiceImpl implements LLMTraceService {

    private static final Logger logger = LoggerFactory.getLogger(LLMTraceServiceImpl.class);

    @Autowired(required = false)
    private ObservabilityConfig config;

    @Autowired
    private ElasticsearchObservabilityService esService;

    @Autowired(required = false)
    @Qualifier("observabilityExecutor")
    private Executor executor;

    // ==================== 公开方法供 Controller 使用 ====================

    public ElasticsearchObservabilityService getEsService() {
        return esService;
    }

    public List<String> getModels() {
        return esService.getModels();
    }

    public List<String> getProviders() {
        return esService.getProviders();
    }

    public List<String> getTraceSources() {
        return esService.getTraceSources();
    }

    // ==================== 接口实现 ====================

    @Override
    public LLMTrace saveTrace(LLMTrace trace) {
        if (!isEnabled()) {
            return trace;
        }

        try {
            LLMTraceDocument doc = toDocument(trace);
            String docId = esService.save(doc);
            if (docId != null) {
                trace.setEsDocId(docId);
            }
            return trace;
        } catch (Exception e) {
            logger.error("保存追踪失败", e);
            return trace;
        }
    }

    @Override
    public Page<LLMTrace> listTraces(String model, String provider, String traceSource,
                                      String conversationId, LocalDateTime startTime, LocalDateTime endTime,
                                      Pageable pageable) {
        if (!isEnabled()) {
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }

        try {
            var result = esService.search(model, provider, traceSource, conversationId,
                    startTime, endTime, pageable.getPageNumber() + 1, pageable.getPageSize());

            List<LLMTrace> traces = result.documents().stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());

            return new PageImpl<>(traces, pageable, result.total());
        } catch (Exception e) {
            logger.error("查询追踪列表失败", e);
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    @Override
    public LLMTrace getTrace(Long id) {
        // 这个方法不再使用，保留兼容性
        throw new NotFoundException("请使用文档ID查询");
    }

    /**
     * 根据 ES 文档 ID 查询
     */
    public LLMTrace getByDocId(String docId) {
        if (!isEnabled()) {
            throw new NotFoundException("ES未启用");
        }

        LLMTraceDocument doc = esService.getById(docId);
        if (doc == null) {
            throw new NotFoundException("追踪不存在");
        }
        return toEntity(doc);
    }

    /**
     * 根据 traceId 字段查询
     */
    public LLMTrace getByTraceId(String traceId) {
        if (!isEnabled()) {
            throw new NotFoundException("ES未启用");
        }

        LLMTraceDocument doc = esService.getByTraceId(traceId);
        if (doc == null) {
            throw new NotFoundException("追踪不存在");
        }
        return toEntity(doc);
    }

    @Override
    public void deleteTrace(Long id) {
        // 这个方法不再使用，保留兼容性
        throw new NotFoundException("请使用文档ID删除");
    }

    /**
     * 根据 ES 文档 ID 删除
     */
    public void deleteByDocId(String docId) {
        esService.delete(docId);
    }

    // ==================== 异步记录方法 ====================

    @Override
    public void recordStart(String traceId, String model, String provider, String conversationId,
                           String requestContent, String traceSource) {
        if (!isEnabled()) return;

        CompletableFuture.runAsync(() -> {
            try {
                LLMTraceDocument doc = new LLMTraceDocument();
                doc.setTraceId(traceId);
                doc.setModel(model);
                doc.setProvider(provider);
                doc.setConversationId(conversationId);
                doc.setRequestContent(requestContent);
                doc.setTraceSource(traceSource);
                doc.setCreatedAt(LocalDateTime.now());
                doc.setStatus(1);  // 默认成功

                String docId = esService.save(doc);
                logger.debug("追踪开始已记录: traceId={}, docId={}", traceId, docId);
            } catch (Exception e) {
                logger.warn("记录追踪开始失败: traceId={}", traceId, e);
            }
        }, getExecutor());
    }

    @Override
    public void recordEnd(String traceId, String responseContent, Integer inputTokens,
                          Integer outputTokens, Integer totalTokens) {
        if (!isEnabled()) return;

        CompletableFuture.runAsync(() -> {
            try {
                // 查找现有文档
                LLMTraceDocument doc = esService.getByTraceId(traceId);
                if (doc == null) {
                    logger.warn("未找到追踪记录: traceId={}", traceId);
                    return;
                }

                // 更新字段
                doc.setResponseContent(responseContent);
                doc.setInputTokens(inputTokens);
                doc.setOutputTokens(outputTokens);
                doc.setTotalTokens(totalTokens);
                doc.setFinishedAt(LocalDateTime.now());
                doc.setStatus(1);

                // 计算延迟
                if (doc.getCreatedAt() != null) {
                    long latency = java.time.Duration.between(doc.getCreatedAt(), doc.getFinishedAt()).toMillis();
                    doc.setLatency(latency);
                }

                esService.update(doc.getId(), doc);
                logger.debug("追踪结束已记录: traceId={}", traceId);
            } catch (Exception e) {
                logger.warn("记录追踪结束失败: traceId={}", traceId, e);
            }
        }, getExecutor());
    }

    @Override
    public void recordError(String traceId, String errorContent, long latency) {
        if (!isEnabled()) return;

        CompletableFuture.runAsync(() -> {
            try {
                // 查找现有文档
                LLMTraceDocument doc = esService.getByTraceId(traceId);
                if (doc == null) {
                    // 创建新文档
                    doc = new LLMTraceDocument();
                    doc.setTraceId(traceId);
                    doc.setCreatedAt(LocalDateTime.now());
                }

                // 更新字段
                doc.setErrorContent(errorContent);
                doc.setLatency(latency > 0 ? latency : 0);
                doc.setFinishedAt(LocalDateTime.now());
                doc.setStatus(0);  // 失败

                if (doc.getId() != null) {
                    esService.update(doc.getId(), doc);
                } else {
                    esService.save(doc);
                }
                logger.debug("追踪错误已记录: traceId={}", traceId);
            } catch (Exception e) {
                logger.warn("记录追踪错误失败: traceId={}", traceId, e);
            }
        }, getExecutor());
    }

    // ==================== 辅助方法 ====================

    private boolean isEnabled() {
        if (config != null && !config.isEnabled()) {
            return false;
        }
        return esService.isEnabled();
    }

    private Executor getExecutor() {
        return executor != null ? executor : CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 实体转文档
     */
    private LLMTraceDocument toDocument(LLMTrace trace) {
        LLMTraceDocument doc = new LLMTraceDocument();
        BeanUtils.copyProperties(trace, doc, "id", "esDocId");
        return doc;
    }

    /**
     * 文档转实体
     */
    private LLMTrace toEntity(LLMTraceDocument doc) {
        LLMTrace trace = new LLMTrace();
        BeanUtils.copyProperties(doc, trace, "id");
        
        // 关键：将 ES 文档 ID 设置到 esDocId 字段
        trace.setEsDocId(doc.getId());
        trace.setId(null);  // Long id 不再使用
        
        return trace;
    }
}
