package com.github.app.dify.chat.service.impl;

import com.github.app.dify.chat.service.OcrService;
import com.github.app.dify.system.config.OcrConfig;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OCR服务实现类
 * 调用本地EasyOCR服务进行图片文字识别
 * 配置从系统配置中读取（通过OcrConfig）
 */
@Service
public class OcrServiceImpl implements OcrService {
    
    private static final Logger logger = LoggerFactory.getLogger(OcrServiceImpl.class);
    
    @Autowired
    private OcrConfig ocrConfig;
    
    private RestTemplate restTemplate;
    
    /**
     * 初始化RestTemplate（在OcrConfig加载配置后执行）
     */
    @PostConstruct
    public void init() {
        initRestTemplate();
    }
    
    /**
     * 初始化RestTemplate（支持配置更新后重新初始化）
     */
    private void initRestTemplate() {
        // 使用OcrConfig中的配置
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ocrConfig.getTimeout());
        factory.setReadTimeout(ocrConfig.getTimeout());
        this.restTemplate = new RestTemplate(factory);
        logger.info("OCR服务初始化完成 - URL: {}, 超时时间: {}ms", 
                ocrConfig.getServiceUrl(), ocrConfig.getTimeout());
    }
    
    /**
     * 重新初始化RestTemplate（用于配置更新后刷新）
     */
    public void reload() {
        initRestTemplate();
        logger.info("OCR服务配置已重新加载");
    }
    
    @Override
    public String recognizeImage(MultipartFile imageFile) throws Exception {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }
        
        try {
            logger.info("开始调用OCR服务识别图片: {}", imageFile.getOriginalFilename());
            
            // 构建请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", imageFile.getResource());
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 调用OCR服务（从OcrConfig获取URL）
            String ocrUrl = ocrConfig.getServiceUrl() + "/ocr";
            logger.debug("调用OCR服务URL: {}", ocrUrl);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    ocrUrl,
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Boolean success = (Boolean) result.get("success");
                
                if (Boolean.TRUE.equals(success)) {
                    String text = (String) result.get("text");
                    logger.info("OCR识别成功，识别到 {} 个字符", text != null ? text.length() : 0);
                    return text != null ? text : "";
                } else {
                    String error = (String) result.get("error");
                    logger.error("OCR识别失败: {}", error);
                    throw new BusinessException("OCR识别失败", ErrorCode.API_CALL_FAILED);
                }
            } else {
                logger.error("OCR服务返回错误状态: {}", response.getStatusCode());
                throw new BusinessException("OCR服务返回错误状态", ErrorCode.API_CALL_FAILED);
            }
            
        } catch (RestClientException e) {
            logger.error("调用OCR服务失败", e);
            throw new BusinessException("调用OCR服务失败", ErrorCode.API_CALL_FAILED, e);
        }
    }
    
    @Override
    public List<String> recognizeImages(List<MultipartFile> imageFiles) throws Exception {
        if (imageFiles == null || imageFiles.isEmpty()) {
            throw new IllegalArgumentException("图片文件列表不能为空");
        }
        
        List<String> results = new ArrayList<>();
        
        for (MultipartFile imageFile : imageFiles) {
            try {
                String text = recognizeImage(imageFile);
                results.add(text);
            } catch (Exception e) {
                logger.error("识别图片失败: {}", imageFile.getOriginalFilename(), e);
                // 继续处理其他图片，但记录错误
                results.add(""); // 添加空字符串作为占位
            }
        }
        
        return results;
    }
    
    @Override
    public boolean isServiceAvailable() {
        try {
            String healthUrl = ocrConfig.getServiceUrl() + "/health";
            logger.debug("检查OCR服务健康状态: {}", healthUrl);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    healthUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                Boolean readerReady = (Boolean) result.get("reader_ready");
                return Boolean.TRUE.equals(readerReady);
            }
            
            return false;
        } catch (Exception e) {
            logger.warn("检查OCR服务健康状态失败", e);
            return false;
        }
    }
}
