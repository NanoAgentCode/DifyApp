package com.github.app.dify.ops.observability.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.ops.observability.config.ObservabilityConfig;
import com.github.app.dify.ops.observability.domain.LLMTrace;
import com.github.app.dify.ops.observability.document.LLMTraceDocument;
import com.github.app.dify.ops.observability.service.ElasticsearchObservabilityService;
import com.github.app.dify.ops.observability.service.LLMTraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * LLM追踪服务实现（仅Elasticsearch存储）
 * 
 * ID 设计：
 * - traceId: 请求级链路ID，同一请求的多次LLM调用共享
 * - spanId: 调用级ID，每次LLM调用唯一（等于ES文档ID）
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
                trace.setSpanId(docId);
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
        throw new NotFoundException("请使用文档ID查询");
    }

    /**
     * 根据 ES 文档 ID（spanId）查询
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

    @Override
    public void deleteTrace(Long id) {
        throw new NotFoundException("请使用文档ID删除");
    }

    /**
     * 根据 ES 文档 ID（spanId）删除
     */
    public void deleteByDocId(String docId) {
        esService.delete(docId);
    }

    // ==================== 异步记录方法 ====================

    @Override
    public CompletableFuture<String> recordStart(String traceId, String model, String provider,
                                                  String conversationId, String requestContent, String traceSource) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 生成 spanId（调用级ID）
                String spanId = UUID.randomUUID().toString();

                LLMTraceDocument doc = new LLMTraceDocument();
                doc.setTraceId(traceId);      // 请求级链路ID
                doc.setSpanId(spanId);        // 调用级ID
                doc.setModel(model);
                doc.setProvider(provider);
                doc.setConversationId(conversationId);
                doc.setRequestContent(requestContent);
                doc.setTraceSource(traceSource);
                doc.setCreatedAt(LocalDateTime.now());
                doc.setStatus(1);  // 默认成功

                // 保存并使用 spanId 作为 ES 文档 ID
                String docId = esService.saveWithId(spanId, doc);
                logger.debug("追踪开始: traceId={}, spanId={}", traceId, spanId);
                return docId;
            } catch (Exception e) {
                logger.warn("记录追踪开始失败: traceId={}", traceId, e);
                return null;
            }
        }, getExecutor());
    }

    @Override
    public void recordEnd(String id, String responseContent, Integer inputTokens,
                          Integer outputTokens, Integer totalTokens) {
        if (!isEnabled() || id == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                // 兼容模式：先尝试作为 spanId（ES文档ID）查询，再尝试作为 traceId 查询
                LLMTraceDocument doc = esService.getById(id);
                String docId = id;
                
                if (doc == null) {
                    // 尝试通过 traceId 字段查询
                    doc = esService.getByTraceId(id);
                    if (doc != null) {
                        docId = doc.getId();  // 使用实际的文档 ID
                    }
                }
                
                if (doc == null) {
                    logger.warn("未找到追踪记录: id={}", id);
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

                esService.update(docId, doc);
                logger.debug("追踪结束: id={}, docId={}", id, docId);
            } catch (Exception e) {
                logger.warn("记录追踪结束失败: id={}", id, e);
            }
        }, getExecutor());
    }

    @Override
    public void recordError(String id, String errorContent, long latency) {
        if (!isEnabled() || id == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                // 兼容模式：先尝试作为 spanId（ES文档ID）查询，再尝试作为 traceId 查询
                LLMTraceDocument doc = esService.getById(id);
                String docId = id;
                
                if (doc == null) {
                    // 尝试通过 traceId 字段查询
                    doc = esService.getByTraceId(id);
                    if (doc != null) {
                        docId = doc.getId();
                    }
                }
                
                if (doc == null) {
                    // 如果没有对应的开始记录，创建新文档
                    doc = new LLMTraceDocument();
                    doc.setTraceId(id);
                    doc.setSpanId(UUID.randomUUID().toString());
                    doc.setCreatedAt(LocalDateTime.now());
                    docId = doc.getSpanId();
                }

                // 更新字段
                doc.setErrorContent(errorContent);
                doc.setLatency(latency > 0 ? latency : 0);
                doc.setFinishedAt(LocalDateTime.now());
                doc.setStatus(0);  // 失败

                esService.update(docId, doc);
                logger.debug("追踪错误: id={}, docId={}", id, docId);
            } catch (Exception e) {
                logger.warn("记录追踪错误失败: id={}", id, e);
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
        
        // ES 文档 ID = spanId
        trace.setEsDocId(doc.getId());
        trace.setSpanId(doc.getSpanId() != null ? doc.getSpanId() : doc.getId());
        trace.setId(null);  // Long id 不再使用
        
        return trace;
    }
}
