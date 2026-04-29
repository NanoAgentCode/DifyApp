package com.github.app.dify.chat.task.resp;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTaskEvent {

    private String eventType;
    private String runId;
    private Long conversationId;
    private Long stepId;
    private String status;
    private String content;
    private String toolName;
    private String toolInputSummary;
    private String toolOutputSummary;
    private Boolean requiresConfirmation;
    private String confirmationId;
    private String riskLevel;
    private String error;
}
