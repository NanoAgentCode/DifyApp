package com.github.app.dify.observability.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.observability.domain.LLMTrace;
import com.github.app.dify.observability.repository.LLMTraceRepository;
import com.github.app.dify.observability.service.LLMTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class LLMTraceServiceImpl implements LLMTraceService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LLMTraceServiceImpl.class);

    @Autowired
    private LLMTraceRepository traceRepository;

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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStart(String traceId, String model, String provider, String conversationId,
            String requestContent, String traceSource) {
        logger.info("Recording trace start, traceId: {}, model: {}", traceId, model);
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
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEnd(String traceId, String responseContent, Integer inputTokens, Integer outputTokens,
            Integer totalTokens) {
        logger.info("Recording trace end, traceId: {}", traceId);
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
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordError(String traceId, String errorContent, long latency) {
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
