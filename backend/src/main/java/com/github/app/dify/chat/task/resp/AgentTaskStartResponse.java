package com.github.app.dify.chat.task.resp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AgentTaskStartResponse {
    private String runId;
    private Long conversationId;
    private String status;
}
