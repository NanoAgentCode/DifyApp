package com.github.app.dify.knowledgebase.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.common.util.SSEResponseUtil;
import com.github.app.dify.knowledgebase.req.KnowledgeBaseQARequest;
import com.github.app.dify.knowledgebase.resp.KnowledgeBaseQAResponse;
import com.github.app.dify.knowledgebase.service.KnowledgeBaseQAService;
import com.github.app.dify.knowledgebase.service.DocumentVectorizationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 知识库问答控制器
 */
@Tag(name = "知识库问答")
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/qa")
public class KnowledgeBaseQAController extends BaseController {
    
    @Autowired
    private KnowledgeBaseQAService knowledgeBaseQAService;
    
    @Autowired(required = false)
    private DocumentVectorizationService documentVectorizationService;
    
    /**
     * 知识库问答（非流式）
     */
    @Operation(summary = "知识库问答")
    @PostMapping
    public ResponseEntity<KnowledgeBaseQAResponse> answer(
            @PathVariable Long kbId,
            @Validated @RequestBody KnowledgeBaseQARequest request,
            HttpServletRequest httpRequest) {
        logger.info("接收到知识库问答请求 - 知识库ID: {}, 问题: {}", kbId, request.getQuestion());
        Long userId = getUserId(httpRequest);
        Object roleObj = httpRequest.getAttribute("role");
        Integer userRole = roleObj instanceof Integer ? (Integer) roleObj : null;
        
        try {
            KnowledgeBaseQAResponse response = knowledgeBaseQAService.answer(kbId, request, userId, userRole);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("权限")) {
                throw new ForbiddenException(e.getMessage());
            }
            throw e;
        }
    }
    
    /**
     * 知识库问答（流式）
     */
    @Operation(summary = "知识库问答（流式）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> answerStream(
            @PathVariable Long kbId,
            @Validated @RequestBody KnowledgeBaseQARequest request,
            HttpServletRequest httpRequest) {
        try {
            logger.info("接收到知识库问答请求（流式） - 知识库ID: {}, 问题: {}", kbId, request.getQuestion());
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer userRole = (Integer) httpRequest.getAttribute("role");
            Flux<KnowledgeBaseQAResponse> responseFlux = knowledgeBaseQAService.answerStream(kbId, request, userId, userRole);
            
            // 转换为SSE格式
            return responseFlux
                    .map(SSEResponseUtil::buildEvent)
                    .onErrorResume(error -> {
                        logger.error("流式问答失败 - 知识库ID: {}", kbId, error);
                        KnowledgeBaseQAResponse errorResponse = new KnowledgeBaseQAResponse();
                        errorResponse.setAnswer("生成答案时发生错误: " + error.getMessage());
                        errorResponse.setFinished(true);
                        return Flux.just(SSEResponseUtil.buildEvent(errorResponse));
                    });
        } catch (Exception e) {
            logger.error("知识库问答失败（流式） - 知识库ID: {}", kbId, e);
            return Flux.error(e);
        }
    }
    
    /**
     * 重新索引文档
     */
    @Operation(summary = "重新索引文档")
    @PostMapping("/documents/{docId}/reindex")
    public ResponseEntity<Void> reindexDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        try {
            logger.info("接收到重新索引文档请求 - 知识库ID: {}, 文档ID: {}", kbId, docId);
            if (documentVectorizationService == null) {
                logger.warn("DocumentVectorizationService未配置，无法重新索引 - 知识库ID: {}, 文档ID: {}", kbId, docId);
                return ResponseEntity.badRequest().build();
            }
            documentVectorizationService.reindexDocument(kbId, docId);
            logger.info("重新索引文档任务已提交 - 知识库ID: {}, 文档ID: {}", kbId, docId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("重新索引文档失败 - 知识库ID: {}, 文档ID: {}", kbId, docId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}