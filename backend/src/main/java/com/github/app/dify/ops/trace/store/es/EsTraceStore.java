package com.github.app.dify.ops.trace.store.es;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.app.dify.ops.trace.model.TraceHandle;
import com.github.app.dify.ops.trace.model.TraceStartRequest;
import com.github.app.dify.ops.trace.store.TraceDocumentGateway;
import com.github.app.dify.ops.trace.model.TraceStep;
import com.github.app.dify.ops.trace.store.TraceStore;
import com.github.app.dify.ops.trace.store.model.TraceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * ES 存储实现。
 */
@Component
public class EsTraceStore implements TraceStore {
    private static final int MAX_ASYNC_RETRY = 2;


    private static final Logger logger = LoggerFactory.getLogger(EsTraceStore.class);

    private final TraceDocumentGateway traceDocumentGateway;
    private final ObjectMapper objectMapper;
    private final Executor traceExecutor;

    public EsTraceStore(TraceDocumentGateway traceDocumentGateway, ObjectMapper objectMapper,
            @Qualifier("traceExecutor") Executor traceExecutor) {
        this.traceDocumentGateway = traceDocumentGateway;
        this.objectMapper = objectMapper;
        this.traceExecutor = traceExecutor;
    }

    @Override
    public TraceHandle start(TraceStartRequest request) {
        TraceHandle handle = new TraceHandle();
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString();
        handle.setTraceId(traceId);
        handle.setSpanId(spanId);
        handle.setTraceSource(request.getTraceSource());

        try {
            TraceRecord record = new TraceRecord();
            record.setId(spanId);
            record.setTraceId(traceId);
            record.setSpanId(spanId);
            record.setConversationId(request.getConversationId());
            record.setModel(request.getModel() != null ? request.getModel() : "business-flow");
            record.setProvider(request.getProvider() != null ? request.getProvider() : "internal");
            record.setRequestContent(request.getRequestSummary());
            record.setTraceSource(request.getTraceSource());
            record.setCreatedAt(LocalDateTime.now());
            record.setStatus(1);
            record.setMetaData(buildInitialMeta(request));
            traceDocumentGateway.saveWithId(spanId, record);
        } catch (Exception e) {
            logger.warn("创建追踪文档失败，降级为内存句柄: traceId={}", traceId, e);
        }
        return handle;
    }

    @Override
    public void end(TraceHandle handle, String responseContent, Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        runAsyncWithRetry(() -> {
            TraceRecord record = resolveRecord(handle);
            if (record == null) {
                return;
            }
            try {
                record.setResponseContent(responseContent);
                record.setInputTokens(inputTokens);
                record.setOutputTokens(outputTokens);
                record.setTotalTokens(totalTokens);
                record.setFinishedAt(LocalDateTime.now());
                record.setStatus(1);
                if (record.getCreatedAt() != null) {
                    long latency = java.time.Duration.between(record.getCreatedAt(), record.getFinishedAt()).toMillis();
                    record.setLatency(latency);
                }
                traceDocumentGateway.update(record.getId(), record);
            } catch (Exception e) {
                throw new RuntimeException("异步更新追踪结束信息失败", e);
            }
        }, "trace_end", handle);
    }

    @Override
    public void error(TraceHandle handle, String errorContent, long latency) {
        runAsyncWithRetry(() -> {
            TraceRecord record = resolveRecord(handle);
            if (record == null) {
                return;
            }
            try {
                record.setErrorContent(errorContent);
                record.setStatus(0);
                record.setFinishedAt(LocalDateTime.now());
                record.setLatency(Math.max(latency, 0));
                traceDocumentGateway.update(record.getId(), record);
            } catch (Exception e) {
                throw new RuntimeException("异步更新追踪错误信息失败", e);
            }
        }, "trace_error", handle);
    }

    @Override
    public void appendStep(TraceHandle handle, TraceStep step) {
        runAsyncWithRetry(() -> {
            TraceRecord record = resolveRecord(handle);
            if (record == null) {
                return;
            }
            try {
                ObjectNode root = getMetaRoot(record.getMetaData());
                ArrayNode steps = root.withArray("steps");
                steps.add(objectMapper.valueToTree(step));

                ObjectNode totals = getOrCreateObject(root, "totals");
                totals.put("stepCount", steps.size());
                int failed = totals.path("failedCount").asInt(0);
                if ("FAILED".equalsIgnoreCase(step.getStatus())) {
                    totals.put("failedCount", failed + 1);
                } else {
                    totals.put("failedCount", failed);
                }

                record.setMetaData(objectMapper.writeValueAsString(root));
                traceDocumentGateway.update(record.getId(), record);
            } catch (Exception e) {
                throw new RuntimeException("异步追加追踪步骤失败", e);
            }
        }, "trace_step", handle);
    }

    private String buildInitialMeta(TraceStartRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("traceVersion", "v1");
        root.put("requestType", request.getRequestType());
        if (request.getBusinessId() != null) {
            root.put("businessId", request.getBusinessId());
        }
        if (request.getUserId() != null) {
            root.put("userId", request.getUserId());
        }
        root.put("startedAt", LocalDateTime.now().toString());
        root.set("steps", objectMapper.createArrayNode());
        ObjectNode totals = root.putObject("totals");
        totals.put("stepCount", 0);
        totals.put("failedCount", 0);
        return root.toString();
    }

    private ObjectNode getMetaRoot(String metaData) {
        if (metaData == null || metaData.isBlank()) {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("steps", objectMapper.createArrayNode());
            return root;
        }
        try {
            JsonNode node = objectMapper.readTree(metaData);
            if (node instanceof ObjectNode objectNode) {
                if (!objectNode.has("steps") || !objectNode.get("steps").isArray()) {
                    objectNode.set("steps", objectMapper.createArrayNode());
                }
                return objectNode;
            }
        } catch (Exception e) {
            logger.debug("解析metaData失败，使用空对象", e);
        }
        ObjectNode root = objectMapper.createObjectNode();
        root.set("steps", objectMapper.createArrayNode());
        return root;
    }

    private TraceRecord resolveRecord(TraceHandle handle) {
        if (handle == null) {
            return null;
        }
        try {
            TraceRecord record = null;
            if (handle.getSpanId() != null && !handle.getSpanId().isBlank()) {
                record = traceDocumentGateway.getById(handle.getSpanId());
            }
            if (record == null && handle.getTraceId() != null && !handle.getTraceId().isBlank()) {
                record = traceDocumentGateway.getByTraceId(handle.getTraceId());
                if (record != null && record.getId() != null) {
                    handle.setSpanId(record.getId());
                }
            }
            return record;
        } catch (Exception e) {
            logger.warn("解析追踪文档失败", e);
            return null;
        }
    }

    private Executor getExecutor() {
        return traceExecutor != null
                ? traceExecutor
                : CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS);
    }

    private void runAsyncWithRetry(Runnable task, String operation, TraceHandle handle) {
        CompletableFuture.runAsync(() -> {
            int attempt = 0;
            while (attempt <= MAX_ASYNC_RETRY) {
                try {
                    task.run();
                    return;
                } catch (Exception ex) {
                    attempt++;
                    if (attempt > MAX_ASYNC_RETRY) {
                        markAsyncFailure(handle, operation, ex);
                        logger.warn("异步追踪操作失败且重试耗尽 - operation: {}, spanId: {}", operation,
                                handle != null ? handle.getSpanId() : null, ex);
                        return;
                    }
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, getExecutor());
    }

    private void markAsyncFailure(TraceHandle handle, String operation, Exception ex) {
        try {
            TraceRecord record = resolveRecord(handle);
            if (record == null) {
                return;
            }
            ObjectNode root = getMetaRoot(record.getMetaData());
            ObjectNode asyncState = getOrCreateObject(root, "asyncState");
            asyncState.put("status", "PARTIAL");
            asyncState.put("lastFailedOperation", operation);
            asyncState.put("lastFailedAt", LocalDateTime.now().toString());
            asyncState.put("lastError", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            record.setMetaData(objectMapper.writeValueAsString(root));
            traceDocumentGateway.update(record.getId(), record);
        } catch (Exception markEx) {
            logger.warn("标记异步追踪失败状态失败: spanId={}", handle != null ? handle.getSpanId() : null, markEx);
        }
    }

    private ObjectNode getOrCreateObject(ObjectNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode objectNode = objectMapper.createObjectNode();
        root.set(fieldName, objectNode);
        return objectNode;
    }
}

