package com.github.app.dify.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatService;
import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.util.RequestHelper;
import com.github.app.dify.common.util.SSEResponseUtil;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 智能问答控制器（直接对话，不使用知识库）
 */
@Tag(name = "智能问答")
@RestController
@RequestMapping("/api/chat")
public class ChatController extends BaseController {
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private QAModelRepository qaModelRepository;
    
    // 注意：智能问答不再使用OCR服务，只支持VL视觉模型处理图片
    // OCR功能仅用于知识库文档上传
    
    /**
     * 智能问答（非流式）
     * 支持两种请求方式：
     * 1. JSON格式（application/json）：传统方式，不支持附件
     * 2. Multipart格式（multipart/form-data）：支持附件上传
     */
    @UserAction(module = "智能问答", actionType = "问答", description = "用户发起智能问答")
    @Operation(summary = "智能问答")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ChatResponse> chat(
            @RequestPart(value = "request", required = false) String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest) {
        try {
            // 使用工具类解析请求
            ChatRequest request = RequestHelper.parseChatRequest(
                    httpRequest, requestJson, files, objectMapper, qaModelRepository);
            
            logger.info("接收到智能问答请求 - 问题: {}", request.getQuestion());
            Long userId = getUserId(httpRequest);
            ChatResponse response = chatService.chat(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("智能问答失败", e);
            throw new BusinessException("智能问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e);
        }
    }
    
    /**
     * 智能问答（流式）
     * 支持两种请求方式：
     * 1. JSON格式（application/json）：传统方式，不支持附件
     * 2. Multipart格式（multipart/form-data）：支持附件上传
     */
    @UserAction(module = "智能问答", actionType = "流式问答", description = "用户发起流式智能问答")
    @Operation(summary = "智能问答（流式）")
    @PostMapping(value = "/stream", 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE,
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestPart(value = "request", required = false) String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest) {
        try {
            // 使用工具类解析请求
            ChatRequest request = RequestHelper.parseChatRequest(
                    httpRequest, requestJson, files, objectMapper, qaModelRepository);
            
            logger.info("接收到智能问答请求（流式） - 问题: {}", request.getQuestion());
            Long userId = getUserId(httpRequest);
            Flux<ChatResponse> responseFlux = chatService.chatStream(request, userId);
            
            // 转换为SSE格式
            return responseFlux
                    .map(SSEResponseUtil::buildEvent)
                    .onErrorResume(error -> {
                        logger.error("流式问答失败", error);
                        ChatResponse errorResponse = new ChatResponse();
                        errorResponse.setAnswer("系统繁忙，请稍后重试");
                        errorResponse.setFinished(true);
                        return Flux.just(SSEResponseUtil.buildEvent(errorResponse));
                    });
        } catch (Exception e) {
            logger.error("智能问答失败（流式）", e);
            return Flux.error(e);
        }
    }
}
