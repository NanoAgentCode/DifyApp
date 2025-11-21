package com.github.app.dify.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.req.KnowledgeBaseQARequest;
import com.github.app.dify.resp.KnowledgeBaseQAResponse;
import com.github.app.dify.service.KnowledgeBaseQAService;
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
 * 知识库问答控制器
 */
@Api(tags = "知识库问答")
@RestController
@RequestMapping("/api/knowledge-bases/{kbId}/qa")
public class KnowledgeBaseQAController {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseQAController.class);
    
    @Autowired
    private KnowledgeBaseQAService knowledgeBaseQAService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 知识库问答（非流式）
     */
    @ApiOperation("知识库问答")
    @PostMapping
    public ResponseEntity<KnowledgeBaseQAResponse> answer(
            @PathVariable Long kbId,
            @Validated @RequestBody KnowledgeBaseQARequest request) {
        try {
            logger.info("接收到知识库问答请求 - 知识库ID: {}, 问题: {}", kbId, request.getQuestion());
            KnowledgeBaseQAResponse response = knowledgeBaseQAService.answer(kbId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("知识库问答失败 - 知识库ID: {}", kbId, e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 知识库问答（流式）
     */
    @ApiOperation("知识库问答（流式）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> answerStream(
            @PathVariable Long kbId,
            @Validated @RequestBody KnowledgeBaseQARequest request) {
        try {
            logger.info("接收到知识库问答请求（流式） - 知识库ID: {}, 问题: {}", kbId, request.getQuestion());
            Flux<KnowledgeBaseQAResponse> responseFlux = knowledgeBaseQAService.answerStream(kbId, request);
            
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
                            KnowledgeBaseQAResponse errorResponse = new KnowledgeBaseQAResponse();
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
            logger.error("知识库问答失败（流式） - 知识库ID: {}", kbId, e);
            return Flux.error(e);
        }
    }
    
    /**
     * 重新索引文档
     */
    @ApiOperation("重新索引文档")
    @PostMapping("/documents/{docId}/reindex")
    public ResponseEntity<Void> reindexDocument(
            @PathVariable Long kbId,
            @PathVariable Long docId) {
        try {
            logger.info("接收到重新索引文档请求 - 知识库ID: {}, 文档ID: {}", kbId, docId);
            // TODO: 实现重新索引逻辑
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("重新索引文档失败 - 知识库ID: {}, 文档ID: {}", kbId, docId, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

