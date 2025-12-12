package com.github.app.dify.chat.controller;

import com.github.app.dify.system.config.DifyConfig;
import com.github.app.dify.chat.req.CreateAiAppReq;
import com.github.app.dify.chat.req.ChatFlowRequest;
import com.github.app.dify.chat.req.WorkFlowRequest;
import com.github.app.dify.chat.req.UpdateAiAppReq;
import com.github.app.dify.chat.resp.AiAppResp;
import com.github.app.dify.chat.resp.DifyResponse;
import com.github.app.dify.chat.service.AiAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AiAppController {
    
    private static final Logger logger = LoggerFactory.getLogger(AiAppController.class);
    
    @Autowired
    private AiAppService aiAppService;
    
    @Autowired
    private DifyConfig difyConfig;
    
    /**
     * 创建AI应用
     */
    @Operation(summary = "创建AI应用")
    @PostMapping
    public ResponseEntity<AiAppResp> createAiApp(@Validated @RequestBody CreateAiAppReq req) {
        // 记录接收到的请求数据
        logger.info("接收到创建应用请求 - 名称: {}, API Key: {}, 类型: {}", 
                req.getName(), req.getAppId(), req.getType());
        AiAppResp resp = aiAppService.createAiApp(req);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 更新AI应用
     */
    @Operation(summary = "更新AI应用")
    @PutMapping("/{id}")
    public ResponseEntity<AiAppResp> updateAiApp(@PathVariable Long id, 
                                                  @Validated @RequestBody UpdateAiAppReq req) {
        try {
            AiAppResp resp = aiAppService.updateAiApp(id, req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 根据ID获取AI应用
     */
    @Operation(summary = "根据ID获取AI应用")
    @GetMapping("/{id}")
    public ResponseEntity<AiAppResp> getAiAppById(@PathVariable Long id) {
        try {
            AiAppResp resp = aiAppService.getAiAppById(id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除AI应用
     */
    @Operation(summary = "删除AI应用")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAiApp(@PathVariable Long id) {
        try {
            aiAppService.deleteAiApp(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
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
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 调用Chat Flow（非流式）
     */
    @Operation(summary = "调用Chat Flow（非流式）")
    @PostMapping("/{id}/chat")
    public Mono<ResponseEntity<DifyResponse>> chat(@PathVariable Long id, 
                                                    @Validated @RequestBody ChatFlowRequest request) {
        return aiAppService.chat(id, request)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    /**
     * 调用Chat Flow（流式）
     */
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
                    logger.error("Controller层：流式Workflow错误恢复: {}", error.getMessage(), error);
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
    
}