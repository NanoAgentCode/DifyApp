package com.github.app.dify.service;

import com.github.app.dify.config.RagConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块服务
 */
@Service
public class ChunkService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkService.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    /**
     * 文档分块
     */
    public List<Chunk> chunkText(String text) {
        return chunkText(text, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
    }
    
    /**
     * 文档分块（自定义参数）
     */
    public List<Chunk> chunkText(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Chunk> chunks = new ArrayList<>();
        
        // 按字符数分块（简单实现，可以改进为按句子或段落分块）
        int textLength = text.length();
        int start = 0;
        int chunkIndex = 0;
        
        while (start < textLength) {
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
                Chunk chunk = new Chunk();
                chunk.setContent(chunkText.trim());
                chunk.setChunkIndex(chunkIndex);
                chunk.setStartIndex(start);
                chunk.setEndIndex(end);
                chunks.add(chunk);
                chunkIndex++;
            }
            
            // 移动到下一个chunk的起始位置（考虑重叠）
            start = end - chunkOverlap;
            if (start <= 0) {
                start = end;
            }
        }
        
        logger.debug("文档分块完成 - 总长度: {}, chunk数量: {}, chunk大小: {}, 重叠: {}", 
                textLength, chunks.size(), chunkSize, chunkOverlap);
        
        return chunks;
    }
    
    /**
     * Chunk数据类
     */
    public static class Chunk {
        private String content;
        private int chunkIndex;
        private int startIndex;
        private int endIndex;
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public int getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(int chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
        
        public int getStartIndex() {
            return startIndex;
        }
        
        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }
        
        public int getEndIndex() {
            return endIndex;
        }
        
        public void setEndIndex(int endIndex) {
            this.endIndex = endIndex;
        }
    }
}

