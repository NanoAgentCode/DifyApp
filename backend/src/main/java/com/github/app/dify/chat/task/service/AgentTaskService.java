package com.github.app.dify.chat.task.service;

import com.github.app.dify.chat.task.req.AgentTaskConfirmRequest;
import com.github.app.dify.chat.task.req.AgentTaskRequest;
import com.github.app.dify.chat.task.resp.AgentTaskEvent;
import com.github.app.dify.chat.task.resp.AgentTaskStartResponse;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AgentTaskService {

    AgentTaskStartResponse startTask(AgentTaskRequest request, Long userId, boolean admin);

    Flux<AgentTaskEvent> streamTaskEvents(String runId, Long userId, boolean admin);

    AgentTaskEvent confirmTask(String runId, AgentTaskConfirmRequest request, Long userId, boolean admin);

    List<AgentTaskEvent> getConversationTaskEvents(Long conversationId, Long userId, boolean admin);
}
