package com.github.app.dify.chat.task.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.chat.service.ChatService;
import com.github.app.dify.chat.task.domain.AgentTaskRun;
import com.github.app.dify.chat.task.domain.AgentTaskStep;
import com.github.app.dify.chat.task.repository.AgentTaskRunRepository;
import com.github.app.dify.chat.task.repository.AgentTaskStepRepository;
import com.github.app.dify.chat.task.req.AgentTaskConfirmRequest;
import com.github.app.dify.chat.task.req.AgentTaskRequest;
import com.github.app.dify.chat.task.resp.AgentTaskEvent;
import com.github.app.dify.chat.task.resp.AgentTaskStartResponse;
import com.github.app.dify.chat.task.service.AgentTaskService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.mcp.browsersearch.McpBrowserSearchService;
import com.github.app.dify.mcp.location.McpLocationService;
import com.github.app.dify.mcp.time.McpTimeService;
import com.github.app.dify.system.domain.AgentSkillConfig;
import com.github.app.dify.system.repository.AgentSkillConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentTaskServiceImpl implements AgentTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AgentTaskServiceImpl.class);
    private static final int MAX_SUMMARY_LENGTH = 1200;

    @Autowired
    private AgentTaskRunRepository taskRunRepository;

    @Autowired
    private AgentTaskStepRepository taskStepRepository;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private McpTimeService mcpTimeService;

    @Autowired
    private McpLocationService mcpLocationService;

    @Autowired
    private McpBrowserSearchService mcpBrowserSearchService;

    @Autowired
    private AgentSkillConfigRepository skillConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public AgentTaskStartResponse startTask(AgentTaskRequest request, Long userId, boolean admin) {
        Long requestConversationId = parseConversationId(request.getConversationId());
        Long conversationId = chatHistoryService.getOrCreateConversation(
                userId, requestConversationId, 4, null, null, request.getQuestion());

        AgentTaskRun run = new AgentTaskRun();
        run.setRunId(UUID.randomUUID().toString());
        run.setConversationId(conversationId);
        run.setUserId(userId);
        run.setQuestion(request.getQuestion());
        run.setStatus("PENDING");
        run.setModelId(request.getModelId());
        run.setEnableBrowserSearch(Boolean.TRUE.equals(request.getEnableBrowserSearch()));
        run.setAdmin(admin);
        run.setDeleted(0);
        run.setCreateTime(new Date());
        run.setUpdateTime(new Date());
        taskRunRepository.save(run);
        return new AgentTaskStartResponse(run.getRunId(), conversationId, run.getStatus());
    }

    @Override
    public Flux<AgentTaskEvent> streamTaskEvents(String runId, Long userId, boolean admin) {
        return Flux.<AgentTaskEvent>create(sink ->
                Schedulers.boundedElastic().schedule(() -> executeTaskStream(runId, userId, admin, sink)));
    }

    private void executeTaskStream(String runId, Long userId, boolean admin, FluxSink<AgentTaskEvent> sink) {
        AgentTaskRun run = getRun(runId, userId, admin);
        if ("SUCCEEDED".equals(run.getStatus()) || "FAILED".equals(run.getStatus())
                || "CANCELLED".equals(run.getStatus()) || "WAITING_CONFIRMATION".equals(run.getStatus())) {
            getRunEvents(run.getRunId()).forEach(sink::next);
            sink.complete();
            return;
        }
        try {
            if ("CONFIRMED".equals(run.getStatus())) {
                emit(sink, saveEvent(run, "tool_result", "RUNNING",
                        "高风险动作已获得确认，将继续进入最终执行链路；现有备忘录等业务能力会在模型问答链路中受控触发。",
                        "memo_create", null, "confirmed=true", false, null, null, null));
                finishRun(run, sink);
                sink.complete();
                return;
            }

            run.setStatus("RUNNING");
            run.setUpdateTime(new Date());
            taskRunRepository.save(run);

            emit(sink, saveEvent(run, "plan_created", "RUNNING", buildPlan(run), null, null, null, false, null, null, null));
            emit(sink, saveEvent(run, "step_started", "RUNNING", "步骤 1：解析任务目标并收集当前环境信息。", null, null, null, false, null, null, null));

            emit(sink, saveEvent(run, "tool_call", "RUNNING", "调用 MCP 时间工具。", "mcp_time", "读取当前默认时区时间", null, false, null, null, null));
            String timeInfo = mcpTimeService.getFormattedTimeInfo();
            emit(sink, saveEvent(run, "tool_result", "RUNNING", "时间上下文已获取。", "mcp_time", null, summarize(timeInfo), false, null, null, null));

            emit(sink, saveEvent(run, "tool_call", "RUNNING", "调用 MCP 位置工具。", "mcp_location", "读取默认地域/时区配置", null, false, null, null, null));
            String locationInfo = mcpLocationService.getFormattedLocationInfo();
            emit(sink, saveEvent(run, "tool_result", "RUNNING", "位置上下文已获取。", "mcp_location", null, summarize(emptyToDefault(locationInfo, "未配置显式地理位置信息")), false, null, null, null));

            if (Boolean.TRUE.equals(run.getEnableBrowserSearch())) {
                emit(sink, saveEvent(run, "tool_call", "RUNNING", "调用 MCP 搜索工具。", "mcp_browser_search", summarize(run.getQuestion()), null, false, null, null, null));
                List<McpBrowserSearchService.SearchResult> results = mcpBrowserSearchService.search(run.getQuestion(), 5);
                emit(sink, saveEvent(run, "tool_result", "RUNNING", "搜索结果已返回。", "mcp_browser_search", null, summarize(formatSearchSummary(results)), false, null, null, null));
            }

            emit(sink, saveEvent(run, "step_started", "RUNNING", "步骤 2：读取当前角色可访问的 skills，作为 Agent 可用能力上下文。", null, null, null, false, null, null, null));
            String skillsContext = loadSkillsContext(Boolean.TRUE.equals(run.getAdmin()));
            emit(sink, saveEvent(run, "tool_result", "RUNNING", "skills 上下文已加载；未配置白名单命令的 skill 仅作为提示词使用。", "skills_context", null, summarize(skillsContext), false, null, null, null));

            if (requiresHighRiskConfirmation(run.getQuestion())) {
                String confirmationId = UUID.randomUUID().toString();
                run.setStatus("WAITING_CONFIRMATION");
                run.setPendingConfirmationId(confirmationId);
                run.setUpdateTime(new Date());
                taskRunRepository.save(run);
                emit(sink, saveEvent(run, "confirmation_required", "WAITING_CONFIRMATION",
                        "检测到可能写入业务数据的动作，需要确认后继续执行。",
                        "memo_create", summarize(run.getQuestion()), null, true, confirmationId, "HIGH", null));
                sink.complete();
                return;
            }

            finishRun(run, sink);
            sink.complete();
        } catch (Exception e) {
            logger.error("任务执行失败: {}", runId, e);
            run.setStatus("FAILED");
            run.setUpdateTime(new Date());
            taskRunRepository.save(run);
            emit(sink, saveEvent(run, "error", "FAILED", "任务执行失败，请稍后重试。", null, null, null, false, null, null, e.getMessage()));
            sink.complete();
        }
    }

    private void emit(FluxSink<AgentTaskEvent> sink, AgentTaskEvent event) {
        if (!sink.isCancelled()) {
            sink.next(event);
        }
    }

    @Override
    @Transactional
    public AgentTaskEvent confirmTask(String runId, AgentTaskConfirmRequest request, Long userId, boolean admin) {
        AgentTaskRun run = getRun(runId, userId, admin);
        if (!"WAITING_CONFIRMATION".equals(run.getStatus())) {
            throw new BusinessException("当前任务不需要确认", ErrorCode.BAD_REQUEST);
        }
        if (request.getConfirmationId() != null && !request.getConfirmationId().equals(run.getPendingConfirmationId())) {
            throw new BusinessException("确认请求已过期", ErrorCode.BAD_REQUEST);
        }

        boolean approved = Boolean.TRUE.equals(request.getApproved());
        AgentTaskEvent event = saveEvent(run, "confirmation_resolved", approved ? "RUNNING" : "CANCELLED",
                approved ? "用户已确认高风险动作，任务将继续执行。" : "用户拒绝高风险动作，任务已取消。",
                "memo_create", null, request.getComment(), false, run.getPendingConfirmationId(), "HIGH", null);

        if (approved) {
            run.setStatus("CONFIRMED");
        } else {
            run.setStatus("CANCELLED");
            chatHistoryService.saveMessage(run.getConversationId(), "user", run.getQuestion());
            chatHistoryService.saveMessage(run.getConversationId(), "assistant", "任务已取消：用户拒绝执行高风险动作。", run.getModelId(), null, null, null);
        }
        run.setPendingConfirmationId(null);
        run.setUpdateTime(new Date());
        taskRunRepository.save(run);
        return event;
    }

    @Override
    public List<AgentTaskEvent> getConversationTaskEvents(Long conversationId, Long userId, boolean admin) {
        if (!admin) {
            taskRunRepository.findTopByConversationIdAndDeletedOrderByCreateTimeDesc(conversationId, 0)
                    .filter(run -> userId.equals(run.getUserId()))
                    .orElseThrow(() -> new BusinessException("无权访问任务会话", ErrorCode.UNAUTHORIZED));
        }
        return taskStepRepository.findByConversationIdAndDeletedOrderByStepIndexAsc(conversationId, 0)
                .stream()
                .map(this::toEvent)
                .collect(Collectors.toList());
    }

    private void finishRun(AgentTaskRun run, FluxSink<AgentTaskEvent> sink) {
        emit(sink, saveEvent(run, "step_started", "RUNNING", "步骤 3：基于计划、工具观察和历史上下文生成最终答案。", null, null, null, false, null, null, null));
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setQuestion(buildAgentQuestion(run));
        chatRequest.setConversationId(String.valueOf(run.getConversationId()));
        chatRequest.setModelId(run.getModelId());
        chatRequest.setEnableBrowserSearch(false);
        chatRequest.setEnableTimeInfo(true);
        ChatResponse response = chatService.chat(chatRequest, run.getUserId());
        String answer = response != null ? response.getAnswer() : "";
        run.setFinalAnswer(answer);
        run.setStatus("SUCCEEDED");
        run.setUpdateTime(new Date());
        taskRunRepository.save(run);
        emit(sink, saveEvent(run, "answer_delta", "RUNNING", answer, null, null, null, false, null, null, null));
        emit(sink, saveEvent(run, "finished", "SUCCEEDED", "任务已完成。", null, null, summarize(answer), false, null, null, null));
    }

    private String buildPlan(AgentTaskRun run) {
        return "1. 明确用户目标和约束。\n"
                + "2. 收集当前时间、位置、可用 skills 和必要的外部信息。\n"
                + "3. 识别是否需要调用高风险工具，必要时暂停等待确认。\n"
                + "4. 汇总观察结果，生成最终回复并写入任务会话历史。\n\n"
                + "任务：" + run.getQuestion();
    }

    private String buildAgentQuestion(AgentTaskRun run) {
        List<AgentTaskEvent> events = getRunEvents(run.getRunId());
        String observations = events.stream()
                .filter(event -> event.getContent() != null || event.getToolOutputSummary() != null)
                .map(event -> "- " + event.getEventType() + ": " + emptyToDefault(event.getContent(), "")
                        + emptyToDefault(event.getToolOutputSummary(), ""))
                .collect(Collectors.joining("\n"));
        return "你正在以任务模式回答用户。请基于显式计划和工具观察给出最终答案，不要暴露隐藏推理链。\n\n"
                + "用户原始任务：\n" + run.getQuestion() + "\n\n"
                + "已执行的计划/工具观察：\n" + observations;
    }

    private boolean requiresHighRiskConfirmation(String question) {
        if (question == null) {
            return false;
        }
        String q = question.toLowerCase();
        return q.contains("备忘") || q.contains("提醒") || q.contains("待办")
                || q.contains("创建") || q.contains("新增") || q.contains("删除") || q.contains("修改")
                || q.contains("memo") || q.contains("todo");
    }

    private String loadSkillsContext(boolean admin) {
        List<AgentSkillConfig> configs = skillConfigRepository.findByDeletedAndEnabledOrderBySkillKeyAsc(0, true);
        List<String> parts = new ArrayList<>();
        for (AgentSkillConfig config : configs) {
            if (!admin && !Boolean.TRUE.equals(config.getVisibleToUser())) {
                continue;
            }
            String skillBody = readSkillBody(config);
            String commandPolicy = hasAllowedCommands(config.getExtJson())
                    ? "已配置 allowedCommands，可在确认后执行白名单脚本。"
                    : "未配置 allowedCommands，仅作为提示词上下文使用。";
            parts.add("## " + config.getSkillKey() + "\n"
                    + emptyToDefault(config.getDescription(), config.getSkillName()) + "\n"
                    + commandPolicy + "\n"
                    + summarize(skillBody));
        }
        return parts.isEmpty() ? "当前角色没有可用 skill。" : String.join("\n\n", parts);
    }

    private String readSkillBody(AgentSkillConfig config) {
        try {
            if (config.getSkillPath() == null || config.getSkillPath().trim().isEmpty()) {
                return "";
            }
            Path root = Paths.get("").toAbsolutePath().normalize();
            Path skillPath = root.resolve(config.getSkillPath()).normalize();
            Path skillMd = Files.isDirectory(skillPath) ? skillPath.resolve("SKILL.md") : skillPath;
            if (!skillMd.normalize().startsWith(root) || !Files.exists(skillMd)) {
                return "";
            }
            return Files.readString(skillMd, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.debug("读取 skill 失败: {}", config.getSkillKey(), e);
            return "";
        }
    }

    private boolean hasAllowedCommands(String extJson) {
        if (extJson == null || extJson.trim().isEmpty()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(extJson);
            JsonNode commands = node.get("allowedCommands");
            return commands != null && commands.isArray() && commands.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatSearchSummary(List<McpBrowserSearchService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "未检索到可用结果。";
        }
        return results.stream()
                .map(result -> result.getTitle() + "\n" + result.getUrl() + "\n" + emptyToDefault(result.getSnippet(), ""))
                .collect(Collectors.joining("\n\n"));
    }

    private AgentTaskRun getRun(String runId, Long userId, boolean admin) {
        AgentTaskRun run = taskRunRepository.findByRunIdAndDeleted(runId, 0)
                .orElseThrow(() -> new BusinessException("任务不存在", ErrorCode.NOT_FOUND));
        if (!admin && !userId.equals(run.getUserId())) {
            throw new BusinessException("无权访问任务", ErrorCode.UNAUTHORIZED);
        }
        return run;
    }

    private List<AgentTaskEvent> getRunEvents(String runId) {
        return taskStepRepository.findByRunIdAndDeletedOrderByStepIndexAsc(runId, 0)
                .stream()
                .map(this::toEvent)
                .collect(Collectors.toList());
    }

    private AgentTaskEvent saveEvent(AgentTaskRun run, String eventType, String status, String content,
                                     String toolName, String toolInputSummary, String toolOutputSummary,
                                     boolean requiresConfirmation, String confirmationId, String riskLevel, String error) {
        AgentTaskStep step = new AgentTaskStep();
        step.setRunId(run.getRunId());
        step.setTaskId(run.getId());
        step.setConversationId(run.getConversationId());
        int stepNumber = (int) taskStepRepository.countByRunIdAndDeleted(run.getRunId(), 0) + 1;
        step.setStepIndex(stepNumber);
        step.setStepNumber(stepNumber);
        step.setEventType(eventType);
        step.setStatus(status);
        step.setContent(content);
        step.setToolName(toolName);
        step.setToolInputSummary(toolInputSummary);
        step.setToolOutputSummary(toolOutputSummary);
        step.setRequiresConfirmation(requiresConfirmation);
        step.setConfirmationId(confirmationId);
        step.setRiskLevel(riskLevel);
        step.setError(error);
        step.setDeleted(0);
        step.setCreateTime(new Date());
        step.setUpdateTime(new Date());
        taskStepRepository.save(step);
        return toEvent(step);
    }

    private AgentTaskEvent toEvent(AgentTaskStep step) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setEventType(step.getEventType());
        event.setRunId(step.getRunId());
        event.setConversationId(step.getConversationId());
        event.setStepId(step.getId());
        event.setStatus(step.getStatus());
        event.setContent(step.getContent());
        event.setToolName(step.getToolName());
        event.setToolInputSummary(step.getToolInputSummary());
        event.setToolOutputSummary(step.getToolOutputSummary());
        event.setRequiresConfirmation(step.getRequiresConfirmation());
        event.setConfirmationId(step.getConfirmationId());
        event.setRiskLevel(step.getRiskLevel());
        event.setError(step.getError());
        return event;
    }

    private Long parseConversationId(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(conversationId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
