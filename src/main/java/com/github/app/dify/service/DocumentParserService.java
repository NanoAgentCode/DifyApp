package com.github.app.dify.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文档解析服务接口
 */
public interface DocumentParserService {
    
    /**
     * 解析文档，提取纯文本内容
     */
    String parseDocument(MultipartFile file);
    
    /**
     * 解析文档输入流，提取纯文本内容
     */
    String parseDocument(InputStream inputStream, String fileName);
    
    /**
     * 检测文档类型
     */
    String detectContentType(MultipartFile file);
}
