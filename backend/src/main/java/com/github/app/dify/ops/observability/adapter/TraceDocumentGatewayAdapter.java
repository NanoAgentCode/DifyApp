package com.github.app.dify.ops.observability.adapter;

import com.github.app.dify.ops.observability.document.LLMTraceDocument;
import com.github.app.dify.ops.observability.service.ElasticsearchObservabilityService;
import com.github.app.dify.ops.trace.store.TraceDocumentGateway;
import com.github.app.dify.ops.trace.store.model.TraceRecord;
import org.springframework.stereotype.Component;

/**
 * Trace 文档网关适配器（ES实现）。
 */
@Component
public class TraceDocumentGatewayAdapter implements TraceDocumentGateway {

    private final ElasticsearchObservabilityService esService;

    public TraceDocumentGatewayAdapter(ElasticsearchObservabilityService esService) {
        this.esService = esService;
    }

    @Override
    public void saveWithId(String id, TraceRecord record) {
        esService.saveWithId(id, toDocument(record));
    }

    @Override
    public TraceRecord getById(String id) {
        LLMTraceDocument doc = esService.getById(id);
        return doc == null ? null : toRecord(doc);
    }

    @Override
    public TraceRecord getByTraceId(String traceId) {
        LLMTraceDocument doc = esService.getByTraceId(traceId);
        return doc == null ? null : toRecord(doc);
    }

    @Override
    public void update(String id, TraceRecord record) {
        esService.update(id, toDocument(record));
    }

    private TraceRecord toRecord(LLMTraceDocument doc) {
        TraceRecord record = new TraceRecord();
        record.setId(doc.getId());
        record.setTraceId(doc.getTraceId());
        record.setSpanId(doc.getSpanId());
        record.setConversationId(doc.getConversationId());
        record.setModel(doc.getModel());
        record.setProvider(doc.getProvider());
        record.setInputTokens(doc.getInputTokens());
        record.setOutputTokens(doc.getOutputTokens());
        record.setTotalTokens(doc.getTotalTokens());
        record.setLatency(doc.getLatency());
        record.setStatus(doc.getStatus());
        record.setRequestContent(doc.getRequestContent());
        record.setResponseContent(doc.getResponseContent());
        record.setErrorContent(doc.getErrorContent());
        record.setMetaData(doc.getMetaData());
        record.setTraceSource(doc.getTraceSource());
        record.setCreatedAt(doc.getCreatedAt());
        record.setFinishedAt(doc.getFinishedAt());
        return record;
    }

    private LLMTraceDocument toDocument(TraceRecord record) {
        LLMTraceDocument doc = new LLMTraceDocument();
        doc.setId(record.getId());
        doc.setTraceId(record.getTraceId());
        doc.setSpanId(record.getSpanId());
        doc.setConversationId(record.getConversationId());
        doc.setModel(record.getModel());
        doc.setProvider(record.getProvider());
        doc.setInputTokens(record.getInputTokens());
        doc.setOutputTokens(record.getOutputTokens());
        doc.setTotalTokens(record.getTotalTokens());
        doc.setLatency(record.getLatency());
        doc.setStatus(record.getStatus());
        doc.setRequestContent(record.getRequestContent());
        doc.setResponseContent(record.getResponseContent());
        doc.setErrorContent(record.getErrorContent());
        doc.setMetaData(record.getMetaData());
        doc.setTraceSource(record.getTraceSource());
        doc.setCreatedAt(record.getCreatedAt());
        doc.setFinishedAt(record.getFinishedAt());
        return doc;
    }
}

