package com.github.app.dify.knowledgebase.service.chunking;

/**
 * 分块配置类
 * 包含分块所需的基本参数
 */
public class ChunkConfig {
    
    private int chunkSize;
    private int chunkOverlap;
    
    public ChunkConfig() {
    }
    
    public ChunkConfig(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }
    
    public int getChunkSize() {
        return chunkSize;
    }
    
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }
    
    public int getChunkOverlap() {
        return chunkOverlap;
    }
    
    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
