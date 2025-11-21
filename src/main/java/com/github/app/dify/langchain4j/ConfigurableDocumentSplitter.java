package com.github.app.dify.langchain4j;

import com.github.app.dify.config.RagConfig;
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
 */
@Component
public class ConfigurableDocumentSplitter implements DocumentSplitter {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurableDocumentSplitter.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Override
    public List<TextSegment> split(Document document) {
        return split(document, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
    }
    
    /**
     * 分割文档（自定义参数）
     */
    public List<TextSegment> split(Document document, int chunkSize, int chunkOverlap) {
        long startTime = System.currentTimeMillis();
        logger.info("开始分割文档 - chunk大小: {}, 重叠: {}", chunkSize, chunkOverlap);
        
        String text = document.text();
        if (text == null || text.trim().isEmpty()) {
            logger.warn("文档文本为空，返回空列表");
            return new ArrayList<>();
        }
        
        List<TextSegment> segments = new ArrayList<>();
        
        // 按字符数分块，尝试在单词边界处截断
        int textLength = text.length();
        logger.info("文档文本长度: {} 字符", textLength);
        int start = 0;
        int chunkIndex = 0;
        int loopCount = 0;
        int maxLoops = (textLength / chunkSize) + 100; // 防止死循环
        
        while (start < textLength) {
            loopCount++;
            if (loopCount > maxLoops) {
                logger.error("文档分割循环次数过多，可能存在死循环 - 文本长度: {}, 当前start: {}, chunk大小: {}", 
                        textLength, start, chunkSize);
                break;
            }
            int end = Math.min(start + chunkSize, textLength);
            String chunkText = text.substring(start, end);
            
            // 尝试在单词边界处截断（避免截断单词）
            if (end < textLength && chunkText.length() == chunkSize) {
                int lastSpace = chunkText.lastIndexOf(' ');
                int lastNewline = chunkText.lastIndexOf('\n');
                int lastPunctuation = Math.max(
                        chunkText.lastIndexOf('。'),
                        Math.max(chunkText.lastIndexOf('！'),
                                chunkText.lastIndexOf('？')));
                
                int bestBreak = Math.max(Math.max(lastSpace, lastNewline), lastPunctuation);
                if (bestBreak > chunkSize * 0.5) { // 至少保留50%的内容
                    chunkText = chunkText.substring(0, bestBreak + 1);
                    end = start + bestBreak + 1;
                }
            }
            
            if (!chunkText.trim().isEmpty()) {
                // 创建TextSegment，添加metadata
                TextSegment segment = TextSegment.from(chunkText.trim());
                
                // 添加metadata（documentId和chunkIndex需要在使用时设置）
                segment.metadata().put("chunkIndex", String.valueOf(chunkIndex));
                segment.metadata().put("startIndex", String.valueOf(start));
                segment.metadata().put("endIndex", String.valueOf(end));
                
                // 保留原始文档的metadata
                if (document.metadata() != null) {
                    document.metadata().asMap().forEach((key, value) -> {
                        segment.metadata().put(key, value != null ? value.toString() : "");
                    });
                }
                
                segments.add(segment);
                chunkIndex++;
            }
            
            // 移动到下一个chunk的起始位置（考虑重叠）
            int nextStart = end - chunkOverlap;
            if (nextStart <= start) {
                // 防止无限循环：如果下一个start位置没有前进，强制前进
                nextStart = end;
            }
            start = nextStart;
            
            // 每处理100个chunk记录一次进度
            if (chunkIndex % 100 == 0 && chunkIndex > 0) {
                logger.info("文档分割进度 - 已处理 {} 个segment, 当前位置: {}/{}", 
                        chunkIndex, start, textLength);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("文档分割完成 - 总长度: {}, segment数量: {}, chunk大小: {}, 重叠: {}, 耗时: {} 毫秒", 
                textLength, segments.size(), chunkSize, chunkOverlap, duration);
        
        return segments;
    }
}

