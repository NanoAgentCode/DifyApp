package com.github.app.dify.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.chat.resp.ChatResponse;
import com.github.app.dify.chat.service.ChatService;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
/**
 * 智能问答控制器（直接对话，不使用知识库）
 */
@Tag(name = "智能问答")
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    
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
    @Operation(summary = "智能问答")
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ChatResponse> chat(
            @RequestPart(value = "request", required = false) String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest) {
        try {
            ChatRequest request;
            String contentType = httpRequest.getContentType();
            
            // 根据Content-Type决定如何解析请求
            if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                // 处理multipart请求
                if (requestJson == null || requestJson.trim().isEmpty()) {
                    logger.error("Multipart请求中缺少request参数");
                    return ResponseEntity.badRequest().build();
                }
                // 从multipart中解析JSON
                request = objectMapper.readValue(requestJson, ChatRequest.class);
                
                // 处理附件（图片）
                if (files != null && !files.isEmpty()) {
                    logger.info("接收到 {} 个附件文件", files.size());
                    
                    // 获取模型信息，检查是否支持多模态
                    QAModel qaModel = null;
                    if (request.getModelId() != null) {
                        qaModel = qaModelRepository.findById(request.getModelId()).orElse(null);
                        logger.debug("从请求中获取模型ID: {}, 模型: {}", request.getModelId(), 
                                qaModel != null ? qaModel.getName() : "未找到");
                    }
                    
                    // 如果没有指定模型ID，尝试获取默认模型
                    if (qaModel == null) {
                        try {
                            java.util.Optional<QAModel> defaultModel = qaModelRepository.findDefaultByUseFor("chat");
                            if (defaultModel.isPresent()) {
                                qaModel = defaultModel.get();
                                logger.debug("使用默认模型: {}", qaModel.getName());
                            } else {
                                // 尝试获取第一个启用的模型
                                java.util.List<QAModel> enabledModels = qaModelRepository.findByUseFor("chat");
                                if (!enabledModels.isEmpty()) {
                                    qaModel = enabledModels.get(0);
                                    logger.debug("使用第一个可用模型: {}", qaModel.getName());
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("获取默认模型失败", e);
                        }
                    }
                    
                    boolean supportsVision = qaModel != null && 
                                           Boolean.TRUE.equals(qaModel.getSupportsVision()) &&
                                           Boolean.TRUE.equals(qaModel.getSupportsMultimodal());
                    
                    if (qaModel != null) {
                        logger.info("当前使用模型: {} (ID: {}), 支持视觉输入: {}, 支持多模态: {}", 
                                qaModel.getName(), qaModel.getId(), 
                                qaModel.getSupportsVision(), qaModel.getSupportsMultimodal());
                    }
                    
                    if (supportsVision) {
                        // 模型支持视觉输入，直接使用图片（转换为base64），使用VL模型处理
                        logger.info("模型支持视觉输入，使用多模态模式（VL模型），直接发送图片数据");
                        List<ChatRequest.ImageData> imageDataList = new ArrayList<>();
                        
                        for (MultipartFile file : files) {
                            if (file != null && !file.isEmpty()) {
                                try {
                                    String fileContentType = file.getContentType();
                                    if (fileContentType != null && fileContentType.startsWith("image/")) {
                                        // 将图片转换为base64
                                        byte[] imageBytes = file.getBytes();
                                        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                                        
                                        ChatRequest.ImageData imageData = new ChatRequest.ImageData();
                                        imageData.setBase64(base64Image);
                                        imageData.setMimeType(fileContentType);
                                        imageDataList.add(imageData);
                                        
                                        logger.info("图片已转换为base64: {}, 大小: {} bytes", 
                                                file.getOriginalFilename(), imageBytes.length);
                                    }
                                } catch (Exception e) {
                                    logger.error("处理图片文件失败: {}", file.getOriginalFilename(), e);
                                }
                            }
                        }
                        
                        if (!imageDataList.isEmpty()) {
                            request.setImages(imageDataList);
                            // 如果用户没有输入问题，使用默认问题
                            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                                request.setQuestion("请帮我分析这些图片中的内容。");
                            }
                            logger.info("已添加 {} 张图片到请求中（多模态模式）", imageDataList.size());
                        }
                    } else {
                        // 模型不支持视觉输入，智能问答不支持OCR，拒绝处理图片
                        logger.warn("当前模型不支持视觉输入，智能问答不支持OCR功能，无法处理图片");
                        String modelName = qaModel != null ? qaModel.getName() : "当前模型";
                        String errorMessage = String.format(
                                "抱歉，当前使用的模型（%s）不支持视觉输入，无法处理图片。\n\n" +
                                "请选择支持多模态的视觉模型（如 Qwen-VL、GPT-4 Vision 等）来处理图片。\n\n" +
                                "提示：您可以在\"大模型管理\"中配置支持视觉输入的模型，并开启\"支持多模态\"和\"支持视觉输入\"选项。",
                                modelName
                        );
                        request.setQuestion(errorMessage);
                        // 清空文件列表，避免继续处理
                        files = new ArrayList<>();
                        logger.info("已拒绝处理图片，因为模型不支持视觉输入");
                    }
                }
            } else {
                // 处理JSON请求
                try {
                    // 使用InputStream读取请求体
                    java.io.InputStream inputStream = httpRequest.getInputStream();
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    String jsonBody = outputStream.toString("UTF-8");
                    if (jsonBody == null || jsonBody.trim().isEmpty()) {
                        logger.error("JSON请求体为空");
                        return ResponseEntity.badRequest().build();
                    }
                    request = objectMapper.readValue(jsonBody, ChatRequest.class);
                } catch (Exception e) {
                    logger.error("解析JSON请求失败", e);
                    return ResponseEntity.badRequest().build();
                }
            }
            
            // 确保问题不为空
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                request.setQuestion("请帮我分析这些内容。");
                logger.warn("用户问题为空，使用默认问题");
            }
            
            logger.info("接收到智能问答请求 - 问题: {}", request.getQuestion());
            Long userId = (Long) httpRequest.getAttribute("userId");
            ChatResponse response = chatService.chat(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("智能问答失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 智能问答（流式）
     * 支持两种请求方式：
     * 1. JSON格式（application/json）：传统方式，不支持附件
     * 2. Multipart格式（multipart/form-data）：支持附件上传
     */
    @Operation(summary = "智能问答（流式）")
    @PostMapping(value = "/stream", 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE,
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestPart(value = "request", required = false) String requestJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest) {
        try {
            ChatRequest request;
            String contentType = httpRequest.getContentType();
            
            // 根据Content-Type决定如何解析请求
            if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                // 处理multipart请求
                if (requestJson == null || requestJson.trim().isEmpty()) {
                    logger.error("Multipart请求中缺少request参数（流式）");
                    return Flux.error(new IllegalArgumentException("Multipart请求中缺少request参数"));
                }
                // 从multipart中解析JSON
                request = objectMapper.readValue(requestJson, ChatRequest.class);
                
                // 处理附件（图片）
                if (files != null && !files.isEmpty()) {
                    logger.info("接收到 {} 个附件文件（流式）", files.size());
                    
                    // 获取模型信息，检查是否支持多模态（流式）
                    QAModel qaModel = null;
                    if (request.getModelId() != null) {
                        qaModel = qaModelRepository.findById(request.getModelId()).orElse(null);
                        logger.debug("从请求中获取模型ID（流式）: {}, 模型: {}", request.getModelId(), 
                                qaModel != null ? qaModel.getName() : "未找到");
                    }
                    
                    // 如果没有指定模型ID，尝试获取默认模型
                    if (qaModel == null) {
                        try {
                            java.util.Optional<QAModel> defaultModel = qaModelRepository.findDefaultByUseFor("chat");
                            if (defaultModel.isPresent()) {
                                qaModel = defaultModel.get();
                                logger.debug("使用默认模型（流式）: {}", qaModel.getName());
                            } else {
                                // 尝试获取第一个启用的模型
                                java.util.List<QAModel> enabledModels = qaModelRepository.findByUseFor("chat");
                                if (!enabledModels.isEmpty()) {
                                    qaModel = enabledModels.get(0);
                                    logger.debug("使用第一个可用模型（流式）: {}", qaModel.getName());
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("获取默认模型失败（流式）", e);
                        }
                    }
                    
                    boolean supportsVision = qaModel != null && 
                                           Boolean.TRUE.equals(qaModel.getSupportsVision()) &&
                                           Boolean.TRUE.equals(qaModel.getSupportsMultimodal());
                    
                    if (qaModel != null) {
                        logger.info("当前使用模型（流式）: {} (ID: {}), 支持视觉输入: {}, 支持多模态: {}", 
                                qaModel.getName(), qaModel.getId(), 
                                qaModel.getSupportsVision(), qaModel.getSupportsMultimodal());
                    }
                    
                    if (supportsVision) {
                        // 模型支持视觉输入，直接使用图片（转换为base64），使用VL模型处理
                        logger.info("模型支持视觉输入（流式），使用多模态模式（VL模型），直接发送图片数据");
                        List<ChatRequest.ImageData> imageDataList = new ArrayList<>();
                        
                        for (MultipartFile file : files) {
                            if (file != null && !file.isEmpty()) {
                                try {
                                    String fileContentType = file.getContentType();
                                    if (fileContentType != null && fileContentType.startsWith("image/")) {
                                        // 将图片转换为base64
                                        byte[] imageBytes = file.getBytes();
                                        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                                        
                                        ChatRequest.ImageData imageData = new ChatRequest.ImageData();
                                        imageData.setBase64(base64Image);
                                        imageData.setMimeType(fileContentType);
                                        imageDataList.add(imageData);
                                        
                                        logger.info("图片已转换为base64（流式）: {}, 大小: {} bytes", 
                                                file.getOriginalFilename(), imageBytes.length);
                                    }
                                } catch (Exception e) {
                                    logger.error("处理图片文件失败（流式）: {}", file.getOriginalFilename(), e);
                                }
                            }
                        }
                        
                        if (!imageDataList.isEmpty()) {
                            request.setImages(imageDataList);
                            // 如果用户没有输入问题，使用默认问题
                            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                                request.setQuestion("请帮我分析这些图片中的内容。");
                            }
                            logger.info("已添加 {} 张图片到请求中（多模态模式，流式）", imageDataList.size());
                        }
                    } else {
                        // 模型不支持视觉输入，智能问答不支持OCR，拒绝处理图片
                        logger.warn("当前模型不支持视觉输入（流式），智能问答不支持OCR功能，无法处理图片");
                        String modelName = qaModel != null ? qaModel.getName() : "当前模型";
                        String errorMessage = String.format(
                                "抱歉，当前使用的模型（%s）不支持视觉输入，无法处理图片。\n\n" +
                                "请选择支持多模态的视觉模型（如 Qwen-VL、GPT-4 Vision 等）来处理图片。\n\n" +
                                "提示：您可以在\"大模型管理\"中配置支持视觉输入的模型，并开启\"支持多模态\"和\"支持视觉输入\"选项。",
                                modelName
                        );
                        request.setQuestion(errorMessage);
                        // 清空文件列表，避免继续处理
                        files = new ArrayList<>();
                        logger.info("已拒绝处理图片（流式），因为模型不支持视觉输入");
                    }
                }
            } else {
                // 处理JSON请求（流式）
                try {
                    // 对于流式请求，使用InputStream读取请求体
                    java.io.InputStream inputStream = httpRequest.getInputStream();
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    String jsonBody = outputStream.toString("UTF-8");
                    if (jsonBody == null || jsonBody.trim().isEmpty()) {
                        logger.error("JSON请求体为空（流式）");
                        return Flux.error(new IllegalArgumentException("JSON请求体为空"));
                    }
                    request = objectMapper.readValue(jsonBody, ChatRequest.class);
                } catch (Exception e) {
                    logger.error("解析JSON请求失败（流式）", e);
                    return Flux.error(new IllegalArgumentException("解析JSON请求失败: " + e.getMessage()));
                }
            }
            
            // 确保问题不为空
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                request.setQuestion("请帮我分析这些内容。");
                logger.warn("用户问题为空，使用默认问题（流式）");
            }
            
            logger.info("接收到智能问答请求（流式） - 问题: {}", request.getQuestion());
            Long userId = (Long) httpRequest.getAttribute("userId");
            Flux<ChatResponse> responseFlux = chatService.chatStream(request, userId);
            
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