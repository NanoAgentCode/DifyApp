package com.github.app.dify.chat.task.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.chat.task.req.AgentTaskConfirmRequest;
import com.github.app.dify.chat.task.req.AgentTaskRequest;
import com.github.app.dify.chat.task.resp.AgentTaskEvent;
import com.github.app.dify.chat.task.resp.AgentTaskStartResponse;
import com.github.app.dify.chat.task.service.AgentTaskService;
import com.github.app.dify.common.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@Tag(name = "Agent任务")
@RestController
@RequestMapping("/api/chat/tasks")
public class AgentTaskController extends BaseController {

    @Autowired
    private AgentTaskService agentTaskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Operation(summary = "创建任务运行")
    @PostMapping
    public ResponseEntity<AgentTaskStartResponse> startTask(
            @Valid @RequestBody AgentTaskRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(agentTaskService.startTask(request, userId, isAdmin(httpRequest)));
    }

    @Operation(summary = "获取任务结构化事件流")
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamEvents(@PathVariable String runId, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean admin = isAdmin(httpRequest);
        return agentTaskService.streamTaskEvents(runId, userId, admin).map(this::toSseEvent);
    }

    @Operation(summary = "确认或拒绝高风险工具调用")
    @PostMapping("/{runId}/confirm")
    public ResponseEntity<AgentTaskEvent> confirm(
            @PathVariable String runId,
            @RequestBody AgentTaskConfirmRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(agentTaskService.confirmTask(runId, request, userId, isAdmin(httpRequest)));
    }

    @Operation(summary = "按会话恢复任务事件")
    @GetMapping("/conversations/{conversationId}/events")
    public ResponseEntity<List<AgentTaskEvent>> getConversationEvents(
            @PathVariable Long conversationId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(agentTaskService.getConversationTaskEvents(conversationId, userId, isAdmin(httpRequest)));
    }

    private ServerSentEvent<String> toSseEvent(AgentTaskEvent event) {
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(event)).event(event.getEventType()).build();
        } catch (Exception e) {
            return ServerSentEvent.builder("{\"eventType\":\"error\",\"content\":\"事件序列化失败\"}").event("error").build();
        }
    }

    private boolean isAdmin(HttpServletRequest request) {
        Integer role = getRole(request);
        return role != null && role == 1;
    }
}
