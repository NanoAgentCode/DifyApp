package com.github.app.dify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.req.ChatRequest;
import com.github.app.dify.resp.ChatResponse;
import com.github.app.dify.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 智能问答控制器（直接对话，不使用知识库）
 */
@Api(tags = "智能问答")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 智能问答（非流式）
     */
    @ApiOperation("智能问答")
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Validated @RequestBody ChatRequest request) {
        try {
            logger.info("接收到智能问答请求 - 问题: {}", request.getQuestion());
            ChatResponse response = chatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("智能问答失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 智能问答（流式）
     */
    @ApiOperation("智能问答（流式）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @Validated @RequestBody ChatRequest request) {
        try {
            logger.info("接收到智能问答请求（流式） - 问题: {}", request.getQuestion());
            Flux<ChatResponse> responseFlux = chatService.chatStream(request);
            
            // 转换为SSE格式
            return responseFlux
                    .map(response -> {
                        try {
                            String json = objectMapper.writeValueAsString(response);
                            return ServerSentEvent.<String>builder()
                                    .data(json)
                                    .build();
                        } catch (Exception e) {
                            logger.error("序列化响应失败", e);
                            return ServerSentEvent.<String>builder()
                                    .data("{\"error\":\"序列化失败\"}")
                                    .build();
                        }
                    })
                    .onErrorResume(error -> {
                        logger.error("流式问答失败", error);
                        try {
                            ChatResponse errorResponse = new ChatResponse();
                            errorResponse.setAnswer("生成答案时发生错误: " + error.getMessage());
                            errorResponse.setFinished(true);
                            String json = objectMapper.writeValueAsString(errorResponse);
                            return Flux.just(ServerSentEvent.<String>builder()
                                    .data(json)
                                    .build());
                        } catch (Exception e) {
                            return Flux.just(ServerSentEvent.<String>builder()
                                    .data("{\"error\":\"处理错误失败\"}")
                                    .build());
                        }
                    });
        } catch (Exception e) {
            logger.error("智能问答失败（流式）", e);
            return Flux.error(e);
        }
    }
}

