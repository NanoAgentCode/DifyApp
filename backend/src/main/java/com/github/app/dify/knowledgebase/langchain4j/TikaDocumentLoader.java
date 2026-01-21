package com.github.app.dify.knowledgebase.langchain4j;

import com.github.app.dify.knowledgebase.service.DocumentParserService;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
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
                    throw new BusinessException("图片OCR识别结果为空，无法向量化", ErrorCode.DATA_VALIDATION_FAILED);
                } else {
                    logger.warn("文档解析结果为空 - 文件名: {}, 类型: {}", fileName, contentType);
                    throw new BusinessException("文档解析结果为空，无法向量化", ErrorCode.DATA_VALIDATION_FAILED);
                }
            }
            
            Document document = Document.from(text);
            
            // 添加metadata
            String fileName = file.getOriginalFilename();
            document.metadata().put("fileName", fileName);
            document.metadata().put("fileSize", String.valueOf(file.getSize()));
            document.metadata().put("contentType", file.getContentType());
            
            // 从文件名提取文件扩展名，用于智能分块策略
            if (fileName != null && fileName.contains(".")) {
                String fileType = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                document.metadata().put("fileType", fileType);
            }
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", fileName, text.length());
            
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
                throw new BusinessException("文档解析结果为空，无法向量化", ErrorCode.DATA_VALIDATION_FAILED);
            }
            
            Document document = Document.from(text);
            
            // 添加metadata
            document.metadata().put("fileName", fileName);
            
            // 从文件名提取文件扩展名，用于智能分块策略
            if (fileName != null && fileName.contains(".")) {
                String fileType = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
                document.metadata().put("fileType", fileType);
            }
            
            logger.info("文档加载成功 - 文件名: {}, 内容长度: {}", fileName, text.length());
            
            return document;
        } catch (Exception e) {
            logger.error("文档加载失败 - 文件名: {}", fileName, e);
            // 如果已经是RuntimeException，直接抛出；否则包装
            throw e;
        }
    }
}
