package com.github.app.dify.chat.task.repository;

import com.github.app.dify.chat.task.domain.AgentTaskStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentTaskStepRepository extends JpaRepository<AgentTaskStep, Long> {

    List<AgentTaskStep> findByRunIdAndDeletedOrderByStepIndexAsc(String runId, Integer deleted);

    List<AgentTaskStep> findByConversationIdAndDeletedOrderByStepIndexAsc(Long conversationId, Integer deleted);

    long countByRunIdAndDeleted(String runId, Integer deleted);
}
