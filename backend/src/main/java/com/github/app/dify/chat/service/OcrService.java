package com.github.app.dify.chat.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OCR服务接口
 * 用于调用本地EasyOCR服务进行图片文字识别
 */
public interface OcrService {
    
    /**
     * 识别单张图片中的文字
     * 
     * @param imageFile 图片文件
     * @return 识别出的文字内容
     * @throws Exception 识别失败时抛出异常
     */
    String recognizeImage(MultipartFile imageFile) throws Exception;
    
    /**
     * 识别多张图片中的文字
     * 
     * @param imageFiles 图片文件列表
     * @return 识别出的文字内容列表（按顺序对应）
     * @throws Exception 识别失败时抛出异常
     */
    List<String> recognizeImages(List<MultipartFile> imageFiles) throws Exception;
    
    /**
     * 检查OCR服务是否可用
     * 
     * @return true表示服务可用，false表示不可用
     */
    boolean isServiceAvailable();
}
