package com.github.app.dify.knowledgebase.langchain4j;

import com.github.app.dify.knowledgebase.service.DocumentParserService;
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
            
            // 检查文本是否为空
            if (text == null || text.trim().isEmpty()) {
                String fileName = file.getOriginalFilename();
                String contentType = file.getContentType();
                
                // 如果是图片文件，说明OCR识别结果为空
                if (contentType != null && contentType.startsWith("image/")) {
                    logger.warn("图片文件OCR识别结果为空 - 文件名: {}, 类型: {}", fileName, contentType);
                    throw new RuntimeException("图片OCR识别结果为空，无法进行向量化。可能原因：1) 图片中没有可识别的文字内容；2) 图片质量较差（模糊、对比度低等）；3) OCR服务异常。请检查图片内容或OCR服务状态。");
                } else {
                    logger.warn("文档解析结果为空 - 文件名: {}, 类型: {}", fileName, contentType);
                    throw new RuntimeException("文档解析结果为空，无法进行向量化。请检查文档内容。");
                }
            }
            
            Document document = Document.from(text);
            
            // 添加metadata
            document.metadata().put("fileName", file.getOriginalFilename());
            document.metadata().put("fileSize", String.valueOf(file.getSize()));
            document.metadata().put("contentType", file.getContentType());
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", file.getOriginalFilename(), text.length());
            
            return document;
        } catch (Exception e) {
            logger.error("文档加载失败 - 文件名: {}", file.getOriginalFilename(), e);
            // 如果已经是RuntimeException，直接抛出；否则包装
            throw e;
        }
    }
    
    /**
     * 从InputStream加载文档
     */
    public Document load(InputStream inputStream, String fileName) {
        try {
            String text = documentParserService.parseDocument(inputStream, fileName);
            
            // 检查文本是否为空
            if (text == null || text.trim().isEmpty()) {
                logger.warn("文档解析结果为空 - 文件名: {}", fileName);
                throw new RuntimeException("文档解析结果为空，无法进行向量化。请检查文档内容。");
            }
            
            Document document = Document.from(text);
            
            // 添加metadata
            document.metadata().put("fileName", fileName);
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", fileName, text.length());
            
            return document;
        } catch (Exception e) {
            logger.error("文档加载失败 - 文件名: {}", fileName, e);
            // 如果已经是RuntimeException，直接抛出；否则包装
            throw e;
        }
    }
}