package com.github.app.dify.chat.task.req;

import com.github.app.dify.chat.req.ChatRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AgentTaskRequest {

    @NotBlank(message = "任务内容不能为空")
    private String question;

    private String conversationId;

    private String userId;

    private List<ChatRequest.Message> history;

    private Long modelId;

    private Boolean enableBrowserSearch;
}
