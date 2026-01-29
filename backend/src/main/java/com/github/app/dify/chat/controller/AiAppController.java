package com.github.app.dify.chat.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.system.config.DifyConfig;
import com.github.app.dify.chat.req.CreateAiAppReq;
import com.github.app.dify.chat.req.ChatFlowRequest;
import com.github.app.dify.chat.req.WorkFlowRequest;
import com.github.app.dify.chat.req.UpdateAiAppReq;
import com.github.app.dify.chat.resp.AiAppResp;
import com.github.app.dify.chat.resp.DifyResponse;
import com.github.app.dify.chat.service.AiAppService;
import com.github.app.dify.ops.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI应用控制器
 */
@Tag(name = "AI应用管理")
@RestController
@RequestMapping("/api/ai-apps")
public class AiAppController extends BaseController {
    
    @Autowired
    private AiAppService aiAppService;
    
    @Autowired
    private DifyConfig difyConfig;
    
    /**
     * 创建AI应用
     */
    @UserAction(module = "AI应用管理", actionType = "创建应用", description = "创建AI应用")
    @Operation(summary = "创建AI应用")
    @PostMapping
    public ResponseEntity<AiAppResp> createAiApp(@Validated @RequestBody CreateAiAppReq req) {
        logger.info("接收到创建应用请求 - 名称: {}, API Key: {}, 类型: {}", 
                req.getName(), req.getAppId(), req.getType());
        AiAppResp resp = aiAppService.createAiApp(req);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 更新AI应用
     */
    @UserAction(module = "AI应用管理", actionType = "更新应用", description = "更新AI应用配置")
    @Operation(summary = "更新AI应用")
    @PutMapping("/{id}")
    public ResponseEntity<AiAppResp> updateAiApp(@PathVariable Long id, 
                                                  @Validated @RequestBody UpdateAiAppReq req) {
        AiAppResp resp = aiAppService.updateAiApp(id, req);
        if (resp == null) {
            throw new NotFoundException("AI应用不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 根据ID获取AI应用
     */
    @Operation(summary = "根据ID获取AI应用")
    @GetMapping("/{id}")
    public ResponseEntity<AiAppResp> getAiAppById(@PathVariable Long id) {
        AiAppResp resp = aiAppService.getAiAppById(id);
        if (resp == null) {
            throw new NotFoundException("AI应用不存在: " + id);
        }
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除AI应用
     */
    @UserAction(module = "AI应用管理", actionType = "删除应用", description = "删除AI应用")
    @Operation(summary = "删除AI应用")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAiApp(@PathVariable Long id) {
        aiAppService.deleteAiApp(id);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取应用列表
     */
    @Operation(summary = "获取应用列表")
    @GetMapping
    public ResponseEntity<?> listAiApps(
            @RequestParam(required = false) Integer tenantId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        try {
            // 如果指定了分页参数，使用分页接口
            if (page != null && pageSize != null && page > 0 && pageSize > 0) {
                com.github.app.dify.common.resp.PageResponse<AiAppResp> pageResponse;
                
                // 如果指定了userId，返回用户可见的应用列表（分页）
                // 注意：用户可见性过滤比较复杂，这里先使用所有应用的分页
                // 实际应用中可能需要优化
                if (userId != null) {
                    // 用户端暂时不支持分页，返回所有可见应用
                    List<AiAppResp> allApps = aiAppService.listVisibleAppsForUser(userId, tenantId, type, status, keyword);
                    // 手动分页
                    int start = (page - 1) * pageSize;
                    int end = Math.min(start + pageSize, allApps.size());
                    List<AiAppResp> content = start < allApps.size() ? allApps.subList(start, end) : java.util.Collections.emptyList();
                    pageResponse = new com.github.app.dify.common.resp.PageResponse<>(content, allApps.size(), page, pageSize);
                } else {
                    // 管理员使用分页接口
                    pageResponse = aiAppService.listAiAppsWithPagination(tenantId, type, status, keyword, page, pageSize);
                }
                
                return ResponseEntity.ok(pageResponse);
            } else {
                // 否则返回所有数据（兼容旧接口）
                List<AiAppResp> resp;
                
                // 如果指定了userId，返回用户可见的应用列表
                if (userId != null) {
                    resp = aiAppService.listVisibleAppsForUser(userId, tenantId, type, status, keyword);
                } else {
                    // 否则返回所有应用（管理员使用）
                    resp = aiAppService.listAiApps(tenantId, type, status, keyword);
                }
                
                return ResponseEntity.ok(resp);
            }
        } catch (Exception e) {
            logger.error("获取应用列表失败", e);
            throw new BusinessException("获取应用列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用Chat Flow（非流式）
     */
    @UserAction(module = "AI应用", actionType = "Chat Flow问答", description = "调用AI应用的Chat Flow")
    @Operation(summary = "调用Chat Flow（非流式）")
    @PostMapping("/{id}/chat")
    public Mono<ResponseEntity<DifyResponse>> chat(@PathVariable Long id, 
                                                    @Validated @RequestBody ChatFlowRequest request) {
        return aiAppService.chat(id, request)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    logger.error("调用Chat Flow失败", error);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
    
    /**
     * 调用Chat Flow（流式）
     */
    @UserAction(module = "AI应用", actionType = "流式Chat Flow问答", description = "调用AI应用的流式Chat Flow")
    @Operation(summary = "调用Chat Flow（流式）")
    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DifyResponse>> chatStream(@PathVariable Long id, 
                                                          @Validated @RequestBody ChatFlowRequest request) {
        logger.info("收到流式Chat请求 - 应用ID: {}, 查询: {}, 用户ID: {}", 
                id, request.getQuery(), request.getUserId());
        return aiAppService.chatStream(id, request)
                .doOnSubscribe(subscription -> {
                    logger.info("Controller层：开始订阅流式响应");
                })
                .doOnNext(response -> {
                    logger.info("Controller层：准备发送SSE响应: event={}, finished={}", 
                            response.getEvent(), response.getFinished());
                })
                .map(response -> {
                    ServerSentEvent.Builder<DifyResponse> builder = ServerSentEvent.<DifyResponse>builder()
                            .data(response);
                    // 如果响应中有event字段，也设置到SSE的event中
                    String event = response.getEvent();
                    if (event != null && !event.isEmpty()) {
                        builder.event(event);
                    }
                    ServerSentEvent<DifyResponse> sse = builder.build();
                    logger.info("Controller层：已构建SSE事件，准备发送给客户端: event={}", event);
                    return sse;
                })
                .doOnComplete(() -> {
                    logger.info("Controller层：流式响应完成");
                })
                .doOnCancel(() -> {
                    logger.warn("Controller层：流式响应被取消");
                })
                .doOnError(error -> {
                    logger.error("Controller层：流式Chat处理出错", error);
                })
                .onErrorResume(error -> {
                    logger.error("Controller层：流式Chat错误恢复: {}", error.getMessage(), error);
                    DifyResponse errorResponse = new DifyResponse();
                    errorResponse.setEvent("error");
                    errorResponse.setAnswer("发生错误: " + error.getMessage());
                    return Flux.just(ServerSentEvent.<DifyResponse>builder()
                            .event("error")
                            .data(errorResponse)
                            .build());
                });
    }
    
    /**
     * 调用Workflow（非流式）
     */
    @UserAction(module = "AI应用", actionType = "Workflow执行", description = "调用AI应用的Workflow")
    @Operation(summary = "调用Workflow（非流式）")
    @PostMapping("/{id}/workflow")
    public Mono<ResponseEntity<DifyResponse>> workflow(@PathVariable Long id, 
                                                       @Validated @RequestBody WorkFlowRequest request,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceIdHeader) {
        // 优先使用 Header 中的 trace_id
        if (traceIdHeader != null && !traceIdHeader.trim().isEmpty() && 
            (request.getTraceId() == null || request.getTraceId().trim().isEmpty())) {
            request.setTraceId(traceIdHeader.trim());
        }
        return aiAppService.workflow(id, request)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    /**
     * 调用Workflow（流式）
     */
    @UserAction(module = "AI应用", actionType = "流式Workflow执行", description = "调用AI应用的流式Workflow")
    @Operation(summary = "调用Workflow（流式）")
    @PostMapping(value = "/{id}/workflow/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DifyResponse>> workflowStream(@PathVariable Long id, 
                                                              @Validated @RequestBody WorkFlowRequest request,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceIdHeader) {
        // 优先使用 Header 中的 trace_id
        if (traceIdHeader != null && !traceIdHeader.trim().isEmpty() && 
            (request.getTraceId() == null || request.getTraceId().trim().isEmpty())) {
            request.setTraceId(traceIdHeader.trim());
        }
        return aiAppService.workflowStream(id, request)
                .map(response -> {
                    ServerSentEvent.Builder<DifyResponse> builder = ServerSentEvent.<DifyResponse>builder()
                            .data(response);
                    // 如果响应中有event字段，也设置到SSE的event中
                    String event = response.getEvent();
                    if (event != null && !event.isEmpty()) {
                        builder.event(event);
                    }
                    return builder.build();
                })
                .onErrorResume(error -> {
                    // 检测是否是客户端断开连接（正常情况，不记录错误）
                    if (isClientAbortError(error)) {
                        logger.debug("客户端中止流式Workflow连接（正常情况，用户可能刷新页面或关闭标签）");
                        return Flux.empty(); // 返回空流，不发送错误消息
                    }
                    
                    logger.error("Controller层：流式Workflow错误恢复: {}", error.getMessage(), error);
                    DifyResponse errorResponse = new DifyResponse();
                    errorResponse.setEvent("error");
                    errorResponse.setAnswer("发生错误: " + error.getMessage());
                    return Flux.just(ServerSentEvent.<DifyResponse>builder()
                            .event("error")
                            .data(errorResponse)
                            .build());
                })
                .doOnCancel(() -> {
                    logger.debug("流式Workflow响应被取消（正常情况）");
                });
    }
    
    /**
     * 获取Dify配置信息（用于前端）
     */
    @Operation(summary = "获取Dify配置信息")
    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("fileUrlPrefix", difyConfig.getFileUrlPrefix());
        return ResponseEntity.ok(config);
    }
    
    /**
     * 上传文件到Dify
     */
    @Operation(summary = "上传文件到Dify")
    @PostMapping(value = "/{id}/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> uploadFile(
            @PathVariable Long id,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "user", required = false) String userId) {
        return aiAppService.uploadFile(id, file, userId)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    logger.error("文件上传失败", error);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
    
    /**
     * 检测是否是客户端中止连接导致的错误
     */
    private boolean isClientAbortError(Throwable error) {
        if (error == null) return false;
        String message = error.getMessage();
        if (message != null) {
            if (message.contains("ClientAbortException") ||
                message.contains("你的主机中的软件中止了一个已建立的连接") ||
                message.contains("Connection reset") ||
                message.contains("Broken pipe") ||
                message.contains("Connection closed") ||
                message.contains("AsyncRequestNotUsableException")) {
                return true;
            }
        }
        Throwable cause = error.getCause();
        return cause != null && cause != error && isClientAbortError(cause);
    }
    
}