package com.github.app.dify.chat.task.repository;

import com.github.app.dify.chat.task.domain.AgentTaskRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentTaskRunRepository extends JpaRepository<AgentTaskRun, Long> {

    Optional<AgentTaskRun> findByRunIdAndDeleted(String runId, Integer deleted);

    Optional<AgentTaskRun> findTopByConversationIdAndDeletedOrderByCreateTimeDesc(Long conversationId, Integer deleted);
}
