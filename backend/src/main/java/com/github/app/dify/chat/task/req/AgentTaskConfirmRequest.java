package com.github.app.dify.chat.task.req;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgentTaskConfirmRequest {
    private String confirmationId;
    private Boolean approved;
    private String comment;
}
