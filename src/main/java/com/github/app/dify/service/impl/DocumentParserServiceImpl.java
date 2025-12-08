package com.github.app.dify.service.impl;

import com.github.app.dify.service.DocumentParserService;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档解析服务
 */
@Service
public class DocumentParserServiceImpl implements DocumentParserService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentParserServiceImpl.class);
    
    private final Tika tika;
    
    public DocumentParserServiceImpl() {
        this.tika = new Tika();
    }
    
    /**
     * 解析文档，提取纯文本内容
     */
    @Override
    public String parseDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            String content = tika.parseToString(inputStream);
            logger.info("文档解析成功 - 文件名: {}, 内容长度: {}", file.getOriginalFilename(), content.length());
            return content != null ? content.trim() : "";
        } catch (IOException | TikaException e) {
            logger.error("文档解析失败 - 文件名: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析文档输入流，提取纯文本内容
     */
    @Override
    public String parseDocument(InputStream inputStream, String fileName) {
        if (inputStream == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        
        try {
            String content = tika.parseToString(inputStream);
            logger.info("文档解析成功 - 文件名: {}, 内容长度: {}", fileName, content.length());
            return content != null ? content.trim() : "";
        } catch (IOException | TikaException e) {
            logger.error("文档解析失败 - 文件名: {}", fileName, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检测文档类型
     */
    @Override
    public String detectContentType(MultipartFile file) {
        try {
            return tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            logger.warn("无法检测文档类型 - 文件名: {}", file.getOriginalFilename(), e);
            return "application/octet-stream";
        }
    }
}

