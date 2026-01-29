package com.github.app.dify.ops.observability.repository;

import com.github.app.dify.ops.observability.domain.LLMTrace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LLMTraceRepository extends JpaRepository<LLMTrace, Long>, JpaSpecificationExecutor<LLMTrace> {

    Page<LLMTrace> findByConversationId(String conversationId, Pageable pageable);

    Page<LLMTrace> findByProvider(String provider, Pageable pageable);

    Page<LLMTrace> findByModel(String model, Pageable pageable);

    Page<LLMTrace> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    java.util.Optional<LLMTrace> findByTraceId(String traceId);
}
