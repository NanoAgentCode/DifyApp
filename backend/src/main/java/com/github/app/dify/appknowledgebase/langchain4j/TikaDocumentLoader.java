package com.github.app.dify.appknowledgebase.langchain4j;

import com.github.app.dify.appknowledgebase.service.DocumentParserService;
import dev.langchain4j.data.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
/**
 * 使用Apache Tika的文档加载器
 */
@Component
public class TikaDocumentLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(TikaDocumentLoader.class);
    
    @Autowired
    private DocumentParserService documentParserService;
    
    /**
     * 从MultipartFile加载文档
     */
    public Document load(MultipartFile file) {
        try {
            String text = documentParserService.parseDocument(file);
            Document document = Document.from(text);
            
            // 添加metadata
            document.metadata().put("fileName", file.getOriginalFilename());
            document.metadata().put("fileSize", String.valueOf(file.getSize()));
            document.metadata().put("contentType", file.getContentType());
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", file.getOriginalFilename(), text.length());
            
            return document;
        } catch (Exception e) {
            logger.error("文档加载失败 - 文件名: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文档加载失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从InputStream加载文档
     */
    public Document load(InputStream inputStream, String fileName) {
        try {
            String text = documentParserService.parseDocument(inputStream, fileName);
            Document document = Document.from(text);
            
            // 添加metadata
            document.metadata().put("fileName", fileName);
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", fileName, text.length());
            
            return document;
        } catch (Exception e) {
            logger.error("文档加载失败 - 文件名: {}", fileName, e);
            throw new RuntimeException("文档加载失败: " + e.getMessage(), e);
        }
    }
}