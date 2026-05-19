package com.github.app.dify.assistant.controller;

import com.github.app.dify.assistant.req.AssistantChatReq;
import com.github.app.dify.assistant.service.AssistantService;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.util.SSEResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "全局页面助手")
@RestController
@RequestMapping("/api/assistant")
public class AssistantController extends BaseController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @Operation(summary = "全局页面助手问答")
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody AssistantChatReq request,
                                             HttpServletRequest httpRequest) {
        try {
            Long userId = getUserId(httpRequest);
            return ResponseEntity.ok(assistantService.chat(request, userId));
        } catch (Exception e) {
            logger.error("全局页面助手问答失败", e);
            throw new BusinessException("全局页面助手问答失败，请稍后重试", ErrorCode.SYSTEM_BUSY, e);
        }
    }

    @Operation(summary = "全局页面助手流式问答")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AssistantChatReq request,
                                                    HttpServletRequest httpRequest) {
        try {
            Long userId = getUserId(httpRequest);
            return assistantService.chatStream(request, userId)
                    .map(SSEResponseUtil::buildEvent)
                    .onErrorResume(error -> {
                        logger.error("全局页面助手流式问答失败", error);
                        ChatResponse response = new ChatResponse();
                        response.setAnswer("页面助手暂时不可用，请稍后重试。");
                        response.setFinished(true);
                        return Flux.just(SSEResponseUtil.buildEvent(response));
                    });
        } catch (Exception e) {
            logger.error("全局页面助手流式问答失败", e);
            return Flux.error(e);
        }
    }
}
