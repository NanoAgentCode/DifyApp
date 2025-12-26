package com.github.app.dify.documentreader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.documentreader.req.DocumentQARequest;
import com.github.app.dify.documentreader.resp.DocumentQAResponse;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import com.github.app.dify.documentreader.service.DocumentReaderService;
import com.github.app.dify.documentreader.service.DocumentReaderQAService;
import com.github.app.dify.documentreader.service.DocumentReaderVectorizationService;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档解读控制器
 */
@Tag(name = "文档解读管理")
@RestController
@RequestMapping("/api/document-reader/documents")
public class DocumentReaderController extends BaseController {
    
    @Autowired
    private DocumentReaderService documentReaderService;
    
    @Autowired(required = false)
    private DocumentReaderQAService documentReaderQAService;
    
    @Autowired(required = false)
    private DocumentReaderVectorizationService documentReaderVectorizationService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取文档列表
     */
    @Operation(summary = "获取文档列表")
    @GetMapping
    public ResponseEntity<PageResponse<DocumentReaderResp>> listDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        PageResponse<DocumentReaderResp> response = documentReaderService.listDocumentsWithPagination(
                userId, keyword, fileType, page, pageSize);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取文档详情
     */
    @Operation(summary = "获取文档详情")
    @GetMapping("/{docId}")
    public ResponseEntity<DocumentReaderResp> getDocumentDetail(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        DocumentReaderResp resp = documentReaderService.getDocumentById(docId, userId);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 上传文档
     */
    @Operation(summary = "上传文档")
    @PostMapping("/upload")
    public ResponseEntity<DocumentReaderResp> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        DocumentReaderResp resp = documentReaderService.uploadDocument(file, userId);
        return ResponseEntity.ok(resp);
    }
    
    /**
     * 删除文档
     */
    @Operation(summary = "删除文档")
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        documentReaderService.deleteDocument(docId, userId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 重新向量化文档
     */
    @Operation(summary = "重新向量化文档")
    @PostMapping("/{docId}/reindex")
    public ResponseEntity<Map<String, Object>> reindexDocument(
            @PathVariable Long docId,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            
            // 验证文档是否存在且属于当前用户
            documentReaderService.getDocumentById(docId, userId);
            
            // 调用重新向量化服务
            if (documentReaderVectorizationService == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "向量化服务未配置");
                return ResponseEntity.ok(response);
            }
            
            documentReaderVectorizationService.reindexDocument(docId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "重新向量化任务已提交，请稍后查看状态");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("重新向量化文档失败 - 文档ID: {}", docId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "重新向量化失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 获取文档内容
     */
    @Operation(summary = "获取文档内容")
    @GetMapping("/{docId}/content")
    public ResponseEntity<InputStreamResource> getDocumentContent(
            @PathVariable Long docId,
            @RequestParam(required = false) Integer page,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        InputStream inputStream = documentReaderService.getDocumentContent(docId, userId, page);
        
        DocumentReaderResp document = documentReaderService.getDocumentById(docId, userId);
        
        InputStreamResource resource = new InputStreamResource(inputStream);
        HttpHeaders headers = new HttpHeaders();
        
        // 处理文件名编码，支持中文文件名
        // 使用Spring的ContentDisposition类来正确构建响应头，避免Tomcat编码问题
        String fileName = document.getOriginalFileName();
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = "document";
        }
        
        // 使用ContentDisposition构建器，只使用RFC 5987格式（filename*），避免在ASCII部分包含Unicode字符
        ContentDisposition contentDisposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        
        headers.setContentDisposition(contentDisposition);
        headers.add(HttpHeaders.CONTENT_TYPE, 
                document.getMimeType() != null ? document.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
    
    /**
     * 获取文档导读
     */
    @Operation(summary = "获取文档导读")
    @GetMapping("/{docId}/guide")
    public ResponseEntity<Map<String, String>> getDocumentGuide(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = documentReaderService.getDocumentGuide(docId, userId);
        Map<String, String> response = new HashMap<>();
        response.put("content", content);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存文档导读
     */
    @Operation(summary = "保存文档导读")
    @PostMapping("/{docId}/guide")
    public ResponseEntity<Void> saveDocumentGuide(
            @PathVariable Long docId,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = requestBody.get("content");
        documentReaderService.saveDocumentGuide(docId, userId, content);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 生成文档导读（使用大模型）
     */
    @Operation(summary = "生成文档导读")
    @PostMapping("/{docId}/guide/generate")
    public ResponseEntity<Map<String, String>> generateDocumentGuide(
            @PathVariable Long docId,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        Long modelId = null;
        if (requestBody != null && requestBody.get("modelId") != null) {
            Object modelIdObj = requestBody.get("modelId");
            if (modelIdObj instanceof Number) {
                modelId = ((Number) modelIdObj).longValue();
            } else if (modelIdObj instanceof String) {
                try {
                    modelId = Long.parseLong((String) modelIdObj);
                } catch (NumberFormatException e) {
                    // 忽略，使用默认模型
                }
            }
        }
        
        String guideContent = documentReaderService.generateDocumentGuide(docId, userId, modelId);
        Map<String, String> response = new HashMap<>();
        response.put("content", guideContent);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 翻译文档
     */
    @Operation(summary = "翻译文档")
    @PostMapping("/{docId}/translate")
    public ResponseEntity<Void> translateDocument(
            @PathVariable Long docId,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String targetLang = requestBody.get("targetLang");
        documentReaderService.translateDocument(docId, userId, targetLang);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取文档翻译内容
     */
    @Operation(summary = "获取文档翻译内容")
    @GetMapping("/{docId}/translation")
    public ResponseEntity<Map<String, String>> getDocumentTranslation(
            @PathVariable Long docId,
            @RequestParam String targetLang,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = documentReaderService.getDocumentTranslation(docId, userId, targetLang);
        Map<String, String> response = new HashMap<>();
        response.put("content", content);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存文档翻译内容
     */
    @Operation(summary = "保存文档翻译内容")
    @PostMapping("/{docId}/translation")
    public ResponseEntity<Void> saveDocumentTranslation(
            @PathVariable Long docId,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String targetLang = requestBody.get("targetLang");
        String content = requestBody.get("content");
        documentReaderService.saveDocumentTranslation(docId, userId, targetLang, content);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取文档脑图
     */
    @Operation(summary = "获取文档脑图")
    @GetMapping("/{docId}/mindmap")
    public ResponseEntity<Map<String, Object>> getDocumentMindMap(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String mindMapData = documentReaderService.getDocumentMindMap(docId, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("mindMapData", mindMapData);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存文档脑图
     */
    @Operation(summary = "保存文档脑图")
    @PostMapping("/{docId}/mindmap")
    public ResponseEntity<Void> saveDocumentMindMap(
            @PathVariable Long docId,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        Object mindMapDataObj = requestBody.get("mindMapData");
        String mindMapData = mindMapDataObj != null ? mindMapDataObj.toString() : null;
        if (mindMapDataObj instanceof Map || mindMapDataObj instanceof String) {
            try {
                mindMapData = objectMapper.writeValueAsString(mindMapDataObj);
            } catch (Exception e) {
                mindMapData = mindMapDataObj.toString();
            }
        }
        documentReaderService.saveDocumentMindMap(docId, userId, mindMapData);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 生成文档脑图（使用大模型）
     */
    @Operation(summary = "生成文档脑图")
    @PostMapping("/{docId}/mindmap/generate")
    public ResponseEntity<Map<String, String>> generateDocumentMindMap(
            @PathVariable Long docId,
            @RequestBody(required = false) Map<String, Object> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        Long modelId = null;
        if (requestBody != null && requestBody.containsKey("modelId")) {
            Object modelIdObj = requestBody.get("modelId");
            if (modelIdObj instanceof Number) {
                modelId = ((Number) modelIdObj).longValue();
            }
        }
        String mindMapData = documentReaderService.generateDocumentMindMap(docId, userId, modelId);
        Map<String, String> response = new HashMap<>();
        response.put("mindMapData", mindMapData);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取文档笔记
     */
    @Operation(summary = "获取文档笔记")
    @GetMapping("/{docId}/notes")
    public ResponseEntity<Map<String, String>> getDocumentNotes(
            @PathVariable Long docId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = documentReaderService.getDocumentNotes(docId, userId);
        Map<String, String> response = new HashMap<>();
        response.put("content", content);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存文档笔记
     */
    @Operation(summary = "保存文档笔记")
    @PostMapping("/{docId}/notes")
    public ResponseEntity<Void> saveDocumentNotes(
            @PathVariable Long docId,
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        String content = requestBody.get("content");
        documentReaderService.saveDocumentNotes(docId, userId, content);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 文档问答（非流式）
     */
    @Operation(summary = "文档问答")
    @PostMapping("/{docId}/qa")
    public ResponseEntity<Map<String, Object>> documentQA(
            @PathVariable Long docId,
            @RequestBody DocumentQARequest qaRequest,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request); // 验证用户身份并获取用户ID
            
            // 调用文档问答服务
            if (documentReaderQAService == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("answer", "文档问答功能开发中");
                response.put("conversationId", qaRequest.getConversationId());
                return ResponseEntity.ok(response);
            }
            
            DocumentQAResponse qaResponse = documentReaderQAService.answer(docId, qaRequest, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("answer", qaResponse.getAnswer());
            response.put("conversationId", qaResponse.getConversationId());
            if (qaResponse.getSources() != null) {
                response.put("sources", qaResponse.getSources());
            }
            return ResponseEntity.ok(response);
        } catch (com.github.app.dify.common.exception.UnauthorizedException e) {
            // 未授权异常，返回401状态码
            logger.warn("文档问答未授权 - 文档ID: {}, 错误: {}", docId, e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("error", e.getMessage());
            response.put("conversationId", qaRequest.getConversationId());
            return ResponseEntity.status(401).body(response);
        } catch (Exception e) {
            logger.error("文档问答失败 - 文档ID: {}", docId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("answer", "文档问答失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            response.put("conversationId", qaRequest.getConversationId());
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 文档问答（流式）
     */
    @Operation(summary = "文档问答（流式）")
    @PostMapping(value = "/{docId}/qa/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> documentQAStream(
            @PathVariable Long docId,
            @RequestBody DocumentQARequest qaRequest,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request); // 验证用户身份并获取用户ID
            
            // 调用文档问答服务（流式）
            if (documentReaderQAService == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("content", "文档问答功能开发中");
                response.put("conversationId", qaRequest.getConversationId());
                response.put("finished", true);
                
                String json = objectMapper.writeValueAsString(response);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("data: " + json + "\n\n")
                        .build());
            }
            
            logger.info("开始处理流式问答请求 - 文档ID: {}, 问题: {}", docId, qaRequest.getQuestion());
            
            return documentReaderQAService.answerStream(docId, qaRequest, userId)
                    .map(qaResponse -> {
                        try {
                            Map<String, Object> responseMap = new HashMap<>();
                            responseMap.put("content", qaResponse.getAnswer());
                            responseMap.put("conversationId", qaResponse.getConversationId());
                            responseMap.put("finished", qaResponse.getFinished() != null ? qaResponse.getFinished() : false);
                            if (qaResponse.getSources() != null) {
                                responseMap.put("sources", qaResponse.getSources());
                            }
                            
                            String json = objectMapper.writeValueAsString(responseMap);
                            logger.debug("发送SSE事件 - 文档ID: {}, finished: {}, content长度: {}", 
                                    docId, responseMap.get("finished"), qaResponse.getAnswer() != null ? qaResponse.getAnswer().length() : 0);
                            return ServerSentEvent.<String>builder()
                                    .data(json)
                                    .build();
                        } catch (Exception e) {
                            logger.error("序列化响应失败", e);
                            return ServerSentEvent.<String>builder()
                                    .data("{\"error\":\"序列化响应失败\"}")
                                    .build();
                        }
                    })
                    .doOnError(error -> logger.error("流式响应处理错误 - 文档ID: {}", docId, error))
                    .doOnComplete(() -> logger.info("流式响应完成 - 文档ID: {}", docId))
                    .onErrorResume(error -> {
                        logger.error("文档问答失败（流式） - 文档ID: {}", docId, error);
                        try {
                            Map<String, Object> errorResponse = new HashMap<>();
                            errorResponse.put("error", error.getMessage());
                            errorResponse.put("finished", true);
                            String json = objectMapper.writeValueAsString(errorResponse);
                            return Flux.just(ServerSentEvent.<String>builder()
                                    .data(json)
                                    .build());
                        } catch (Exception e) {
                            return Flux.error(error);
                        }
                    });
        } catch (com.github.app.dify.common.exception.UnauthorizedException e) {
            // 处理未授权异常，返回友好的错误信息
            logger.warn("文档问答未授权 - 文档ID: {}, 错误: {}", docId, e.getMessage());
            try {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                errorResponse.put("finished", true);
                String json = objectMapper.writeValueAsString(errorResponse);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("data: " + json + "\n\n")
                        .build());
            } catch (Exception ex) {
                logger.error("序列化错误响应失败", ex);
                return Flux.error(e);
            }
        } catch (Exception e) {
            logger.error("文档问答失败（流式） - 文档ID: {}", docId, e);
            try {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "文档问答失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                errorResponse.put("finished", true);
                String json = objectMapper.writeValueAsString(errorResponse);
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("data: " + json + "\n\n")
                        .build());
            } catch (Exception ex) {
                logger.error("序列化错误响应失败", ex);
                return Flux.error(e);
            }
        }
    }
}

