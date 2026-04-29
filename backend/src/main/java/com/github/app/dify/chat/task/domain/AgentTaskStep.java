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
@Table(name = "agent_task_step")
public class AgentTaskStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "step_index")
    private Integer stepIndex;

    @Column(name = "step_number")
    private Integer stepNumber;

    @Column(name = "event_type", length = 60)
    private String eventType;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name", length = 120)
    private String toolName;

    @Column(name = "tool_input_summary", columnDefinition = "TEXT")
    private String toolInputSummary;

    @Column(name = "tool_output_summary", columnDefinition = "TEXT")
    private String toolOutputSummary;

    @Column(name = "requires_confirmation")
    private Boolean requiresConfirmation;

    @Column(name = "confirmation_id", length = 64)
    private String confirmationId;

    @Column(name = "risk_level", length = 40)
    private String riskLevel;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;
}
