package com.github.app.dify.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.app.dify.chat.req.ChatRequest;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.repository.QAModelRepository;
import com.github.app.dify.system.util.SkillLoader;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 请求处理工具类
 */
public class RequestHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestHelper.class);
    
    /**
     * 解析ChatRequest（支持JSON和Multipart格式）
     */
    public static ChatRequest parseChatRequest(
            HttpServletRequest httpRequest,
            String requestJson,
            List<MultipartFile> files,
            ObjectMapper objectMapper,
            QAModelRepository qaModelRepository) throws Exception {
        
        ChatRequest request;
        String contentType = httpRequest.getContentType();
        
        // 根据Content-Type决定如何解析请求
        if (contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            // 处理multipart请求
            if (requestJson == null || requestJson.trim().isEmpty()) {
                throw new IllegalArgumentException("Multipart请求中缺少request参数");
            }
            // 从multipart中解析JSON
            request = objectMapper.readValue(requestJson, ChatRequest.class);
            
            // 处理附件（图片）
            if (files != null && !files.isEmpty()) {
                logger.info("接收到 {} 个附件文件", files.size());
                request = processImageFiles(request, files, qaModelRepository);
            }
        } else {
            // 处理JSON请求
            request = parseJsonRequest(httpRequest, objectMapper);
        }
        
        // 确保问题不为空
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            request.setQuestion(SkillLoader.loadSkill("chat/default_empty_question").trim());
            logger.warn("用户问题为空，使用默认问题");
        }
        
        return request;
    }
    
    /**
     * 解析JSON请求
     */
    private static ChatRequest parseJsonRequest(HttpServletRequest httpRequest, ObjectMapper objectMapper) throws Exception {
        try {
            InputStream inputStream = httpRequest.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            String jsonBody = outputStream.toString("UTF-8");
            if (jsonBody == null || jsonBody.trim().isEmpty()) {
                throw new IllegalArgumentException("JSON请求体为空");
            }
            return objectMapper.readValue(jsonBody, ChatRequest.class);
        } catch (Exception e) {
            logger.error("解析JSON请求失败", e);
            throw new IllegalArgumentException("解析JSON请求失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 处理图片文件
     */
    private static ChatRequest processImageFiles(
            ChatRequest request,
            List<MultipartFile> files,
            QAModelRepository qaModelRepository) {
        
        // 获取模型信息，检查是否支持多模态
        QAModel qaModel = getQAModel(request, qaModelRepository);
        
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
            List<ChatRequest.ImageData> imageDataList = convertFilesToImageData(files);
            
            if (!imageDataList.isEmpty()) {
                request.setImages(imageDataList);
                // 如果用户没有输入问题，使用默认问题
                if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                    request.setQuestion(SkillLoader.loadSkill("chat/default_image_question").trim());
                }
                logger.info("已添加 {} 张图片到请求中（多模态模式）", imageDataList.size());
            }
        } else {
            // 模型不支持视觉输入，智能问答不支持OCR，拒绝处理图片
            logger.warn("当前模型不支持视觉输入，智能问答不支持OCR功能，无法处理图片");
            String modelName = qaModel != null ? qaModel.getName() : "当前模型";
            String errorMessage = SkillLoader.loadSkillWithTemplate("chat/vision_unsupported_message_template",
                    java.util.Map.of("modelName", modelName));
            request.setQuestion(errorMessage);
            logger.info("已拒绝处理图片，因为模型不支持视觉输入");
        }
        
        return request;
    }
    
    /**
     * 获取QAModel
     */
    private static QAModel getQAModel(ChatRequest request, QAModelRepository qaModelRepository) {
        QAModel qaModel = null;
        if (request.getModelId() != null) {
            qaModel = qaModelRepository.findById(request.getModelId()).orElse(null);
            logger.debug("从请求中获取模型ID: {}, 模型: {}", request.getModelId(), 
                    qaModel != null ? qaModel.getName() : "未找到");
        }
        
        // 如果没有指定模型ID，尝试获取默认模型
        if (qaModel == null) {
            try {
                Optional<QAModel> defaultModel = qaModelRepository.findDefaultByUseFor("chat");
                if (defaultModel.isPresent()) {
                    qaModel = defaultModel.get();
                    logger.debug("使用默认模型: {}", qaModel.getName());
                } else {
                    // 尝试获取第一个启用的模型
                    List<QAModel> enabledModels = qaModelRepository.findByUseFor("chat");
                    if (!enabledModels.isEmpty()) {
                        qaModel = enabledModels.get(0);
                        logger.debug("使用第一个可用模型: {}", qaModel.getName());
                    }
                }
            } catch (Exception e) {
                logger.warn("获取默认模型失败", e);
            }
        }
        
        return qaModel;
    }
    
    /**
     * 将文件列表转换为ImageData列表
     */
    private static List<ChatRequest.ImageData> convertFilesToImageData(List<MultipartFile> files) {
        List<ChatRequest.ImageData> imageDataList = new ArrayList<>();
        
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                try {
                    String fileContentType = file.getContentType();
                    if (fileContentType != null && fileContentType.startsWith("image/")) {
                        // 将图片转换为base64
                        byte[] imageBytes = file.getBytes();
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                        
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
        
        return imageDataList;
    }
}
