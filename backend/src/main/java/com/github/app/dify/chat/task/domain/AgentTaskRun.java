package com.github.app.dify.chat.task.domain;

import com.github.app.dify.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_task_run")
public class AgentTaskRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "enable_browser_search")
    private Boolean enableBrowserSearch;

    @Column(name = "is_admin")
    private Boolean admin;

    @Column(name = "final_answer", columnDefinition = "TEXT")
    private String finalAnswer;

    @Column(name = "pending_confirmation_id", length = 64)
    private String pendingConfirmationId;
}
