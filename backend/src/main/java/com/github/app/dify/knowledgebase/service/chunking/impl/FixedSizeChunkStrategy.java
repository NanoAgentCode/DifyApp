package com.github.app.dify.knowledgebase.service.chunking.impl;

import com.github.app.dify.knowledgebase.service.chunking.ChunkConfig;
import com.github.app.dify.knowledgebase.service.chunking.ChunkStrategy;
import com.github.app.dify.knowledgebase.service.chunking.ContentStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块策略
 * 按固定字符数分块，优化边界检测，避免截断单词和句子
 */
@Component
public class FixedSizeChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(FixedSizeChunkStrategy.class);
    
    @Override
    public String getName() {
        return "fixed_size";
    }
    
    @Override
    public boolean supports(String fileType, String contentType) {
        // 固定大小分块作为默认策略，支持所有类型
        return true;
    }
    
    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        int chunkSize = config.getChunkSize();
        int chunkOverlap = config.getChunkOverlap();
        
        List<ChunkResult> chunks = new ArrayList<>();
        int textLength = text.length();
        int start = 0;
        int chunkIndex = 0;
        
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            String chunkText = text.substring(start, end);
            
            // 尝试在合适的边界处截断（避免截断单词和句子）
            if (end < textLength && chunkText.length() == chunkSize) {
                int bestBreak = findBestBreakPoint(chunkText, chunkSize);
                if (bestBreak > chunkSize * 0.5) { // 至少保留50%的内容
                    chunkText = chunkText.substring(0, bestBreak + 1);
                    end = start + bestBreak + 1;
                }
            }
            
            if (!chunkText.trim().isEmpty()) {
                ChunkResult chunk = new ChunkResult();
                chunk.setContent(chunkText.trim());
                chunk.setChunkIndex(chunkIndex);
                chunk.setStartIndex(start);
                chunk.setEndIndex(end);
                chunk.setContentType(ContentStructure.ContentType.TEXT);
                chunks.add(chunk);
                chunkIndex++;
            }
            
            // 移动到下一个chunk的起始位置（考虑重叠）
            start = end - chunkOverlap;
            if (start <= 0) {
                start = end;
            }
        }
        
        logger.debug("固定大小分块完成 - 总长度: {}, chunk数量: {}, chunk大小: {}, 重叠: {}", 
                textLength, chunks.size(), chunkSize, chunkOverlap);
        
        return chunks;
    }
    
    /**
     * 查找最佳截断点
     * 优先级：段落 > 句子 > 单词 > 空格
     */
    private int findBestBreakPoint(String text, int maxLength) {
        // 1. 优先在段落边界（双换行）
        int lastDoubleNewline = text.lastIndexOf("\n\n");
        if (lastDoubleNewline > maxLength * 0.5) {
            return lastDoubleNewline;
        }
        
        // 2. 其次在句子边界（中英文标点）
        int lastSentenceEnd = findLastSentenceEnd(text);
        if (lastSentenceEnd > maxLength * 0.5) {
            return lastSentenceEnd;
        }
        
        // 3. 再次在单词边界（空格）
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace > maxLength * 0.5) {
            return lastSpace;
        }
        
        // 4. 最后在单换行
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline > maxLength * 0.5) {
            return lastNewline;
        }
        
        return -1;
    }
    
    /**
     * 查找最后一个句子结束位置
     */
    private int findLastSentenceEnd(String text) {
        // 中文句子结束标点
        int lastChinesePunct = Math.max(
                Math.max(text.lastIndexOf('。'), text.lastIndexOf('！')),
                text.lastIndexOf('？')
        );
        
        // 英文句子结束标点
        int lastEnglishPunct = Math.max(
                Math.max(text.lastIndexOf('.'), text.lastIndexOf('!')),
                text.lastIndexOf('?')
        );
        
        return Math.max(lastChinesePunct, lastEnglishPunct);
    }
}
