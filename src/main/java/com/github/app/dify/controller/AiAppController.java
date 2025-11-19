package com.github.app.dify.controller;

import com.github.app.dify.req.CreateAiAppReq;
import com.github.app.dify.req.DifyChatRequest;
import com.github.app.dify.req.DifyWorkflowRequest;
import com.github.app.dify.req.UpdateAiAppReq;
import com.github.app.dify.resp.AiAppResp;
import com.github.app.dify.resp.DifyResponse;
import com.github.app.dify.service.AiAppService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AI应用控制器
 */
@Api(tags = "AI应用管理")
@RestController
@RequestMapping("/api/ai-apps")
public class AiAppController {
    
    @Autowired
    private AiAppService aiAppService;
    
    /**
     * 创建AI应用
     */
    @ApiOperation("创建AI应用")
    @PostMapping
    public ResponseEntity<AiAppResp> createAiApp(@Validated @RequestBody CreateAiAppReq req) {
        try {
            AiAppResp resp = aiAppService.createAiApp(req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 更新AI应用
     */
    @ApiOperation("更新AI应用")
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
    @ApiOperation("根据ID获取AI应用")
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
    @ApiOperation("删除AI应用")
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
    @ApiOperation("获取应用列表")
    @GetMapping
    public ResponseEntity<List<AiAppResp>> listAiApps(
            @RequestParam(required = false) Integer tenantId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        try {
            List<AiAppResp> resp = aiAppService.listAiApps(tenantId, type, status);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 调用Chat Flow（非流式）
     */
    @ApiOperation("调用Chat Flow（非流式）")
    @PostMapping("/{id}/chat")
    public Mono<ResponseEntity<DifyResponse>> chat(@PathVariable Long id, 
                                                    @Validated @RequestBody DifyChatRequest request) {
        return aiAppService.chat(id, request)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    /**
     * 调用Chat Flow（流式）
     */
    @ApiOperation("调用Chat Flow（流式）")
    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DifyResponse>> chatStream(@PathVariable Long id, 
                                                          @Validated @RequestBody DifyChatRequest request) {
        return aiAppService.chatStream(id, request)
                .map(response -> ServerSentEvent.<DifyResponse>builder()
                        .data(response)
                        .build())
                .onErrorResume(error -> {
                    DifyResponse errorResponse = new DifyResponse();
                    errorResponse.setEvent("error");
                    return Flux.just(ServerSentEvent.<DifyResponse>builder()
                            .data(errorResponse)
                            .build());
                });
    }
    
    /**
     * 调用Workflow（非流式）
     */
    @ApiOperation("调用Workflow（非流式）")
    @PostMapping("/{id}/workflow")
    public Mono<ResponseEntity<DifyResponse>> workflow(@PathVariable Long id, 
                                                       @Validated @RequestBody DifyWorkflowRequest request) {
        return aiAppService.workflow(id, request)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.badRequest().build());
    }
    
    /**
     * 调用Workflow（流式）
     */
    @ApiOperation("调用Workflow（流式）")
    @PostMapping(value = "/{id}/workflow/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<DifyResponse>> workflowStream(@PathVariable Long id, 
                                                              @Validated @RequestBody DifyWorkflowRequest request) {
        return aiAppService.workflowStream(id, request)
                .map(response -> ServerSentEvent.<DifyResponse>builder()
                        .data(response)
                        .build())
                .onErrorResume(error -> {
                    DifyResponse errorResponse = new DifyResponse();
                    errorResponse.setEvent("error");
                    return Flux.just(ServerSentEvent.<DifyResponse>builder()
                            .data(errorResponse)
                            .build());
                });
    }
}

