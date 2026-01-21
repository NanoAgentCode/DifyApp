package com.github.app.dify.knowledgebase.langchain4j;

import com.github.app.dify.knowledgebase.service.chunking.*;
import com.github.app.dify.system.config.RagConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
/**
 * 可配置的文档分割器，使用现有配置
 * 集成新的分块策略系统，根据文件类型自动选择合适的分块方式
 */
@Component
public class ConfigurableDocumentSplitter implements DocumentSplitter {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurableDocumentSplitter.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired
    private ChunkStrategySelector strategySelector;
    
    @Autowired
    private ContentAnalyzer contentAnalyzer;
    
    @Autowired
    private MixedContentChunker mixedContentChunker;
    
    @Override
    public List<TextSegment> split(Document document) {
        return split(document, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
    }
    
    /**
     * 分割文档（自定义参数）
     * 使用新的分块策略系统，根据文件类型自动选择合适的分块方式
     */
    public List<TextSegment> split(Document document, int chunkSize, int chunkOverlap) {
        long startTime = System.currentTimeMillis();
        logger.info("开始分割文档 - chunk大小: {}, 重叠: {}", chunkSize, chunkOverlap);
        
        String text = document.text();
        if (text == null || text.trim().isEmpty()) {
            logger.warn("文档文本为空，返回空列表");
            return new ArrayList<>();
        }
        
        // 从文档metadata获取文件类型
        String fileType = null;
        if (document.metadata() != null) {
            fileType = document.metadata().get("fileType");
            if (fileType == null || fileType.isEmpty()) {
                // 尝试从文件名推断
                String fileName = document.metadata().get("fileName");
                if (fileName != null && fileName.contains(".")) {
                    fileType = fileName.substring(fileName.lastIndexOf('.') + 1);
                }
            }
        }
        
        logger.info("文档文本长度: {} 字符, 文件类型: {}", text.length(), fileType != null ? fileType : "未知");
        
        // 选择分块策略
        List<ChunkStrategy> strategies = strategySelector.selectStrategy(fileType, text);
        
        // 创建分块配置
        ChunkConfig config = new ChunkConfig(chunkSize, chunkOverlap);
        
        // 如果选择了多个策略（混合内容），使用混合分块器
        if (strategies.size() > 1) {
            logger.info("检测到混合内容，使用混合分块策略");
            ContentStructure structure = contentAnalyzer.analyzeText(text, fileType);
            List<ChunkStrategy.ChunkResult> chunkResults = mixedContentChunker.chunk(structure, config);
            return convertToTextSegments(chunkResults, document);
        }
        
        // 使用单个策略
        ChunkStrategy strategy = strategies.get(0);
        logger.info("使用分块策略: {}", strategy.getName());
        
        List<ChunkStrategy.ChunkResult> chunkResults = strategy.chunk(text, config);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("文档分割完成 - 总长度: {}, chunk数量: {}, 策略: {}, 耗时: {} 毫秒", 
                text.length(), chunkResults.size(), strategy.getName(), duration);
        
        return convertToTextSegments(chunkResults, document);
    }
    
    /**
     * 将ChunkResult转换为TextSegment
     */
    private List<TextSegment> convertToTextSegments(List<ChunkStrategy.ChunkResult> chunkResults, Document document) {
        List<TextSegment> segments = new ArrayList<>();
        
        for (ChunkStrategy.ChunkResult chunkResult : chunkResults) {
            TextSegment segment = TextSegment.from(chunkResult.getContent());
            
            // 添加基本metadata
            segment.metadata().put("chunkIndex", String.valueOf(chunkResult.getChunkIndex()));
            segment.metadata().put("startIndex", String.valueOf(chunkResult.getStartIndex()));
            segment.metadata().put("endIndex", String.valueOf(chunkResult.getEndIndex()));
            
            // 添加内容类型
            if (chunkResult.getContentType() != null) {
                segment.metadata().put("contentType", chunkResult.getContentType());
            }
            
            // 添加额外metadata
            if (chunkResult.getMetadata() != null) {
                chunkResult.getMetadata().forEach(segment.metadata()::put);
            }
            
            // 保留原始文档的metadata
            if (document.metadata() != null) {
                document.metadata().toMap().forEach((key, value) -> {
                    if (!segment.metadata().containsKey(key)) {
                        segment.metadata().put(key, value != null ? value.toString() : "");
                    }
                });
            }
            
            segments.add(segment);
        }
        
        return segments;
    }
}