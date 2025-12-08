package com.github.app.dify.service;

import java.util.List;
/**
 * 文档分块服务接口
 */
public interface ChunkService {
    
    /**
     * 文档分块
     */
    List<Chunk> chunkText(String text);
    
    /**
     * 文档分块（自定义参数）
     */
    List<Chunk> chunkText(String text, int chunkSize, int chunkOverlap);
    
    /**
     * Chunk数据类
     */
    class Chunk {
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