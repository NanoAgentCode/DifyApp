package com.github.app.dify.knowledgebase.service.chunking;

import java.util.List;

/**
 * 分块策略接口
 * 定义不同文件类型和内容的分块方式
 */
public interface ChunkStrategy {
    
    /**
     * 执行分块操作
     * 
     * @param text 待分块的文本内容
     * @param config 分块配置（chunkSize, chunkOverlap等）
     * @return 分块结果列表
     */
    List<ChunkResult> chunk(String text, ChunkConfig config);
    
    /**
     * 判断该策略是否支持指定的文件类型和内容类型
     * 
     * @param fileType 文件类型（扩展名，如 "txt", "md", "docx"）
     * @param contentType 内容类型（"text", "table", "code", "heading"等）
     * @return 是否支持
     */
    boolean supports(String fileType, String contentType);
    
    /**
     * 获取策略名称
     * 
     * @return 策略名称
     */
    String getName();
    
    /**
     * 分块结果
     */
    class ChunkResult {
        private String content;
        private int chunkIndex;
        private int startIndex;
        private int endIndex;
        private String contentType; // 内容类型：text, table, code, heading等
        private java.util.Map<String, String> metadata; // 额外元数据
        
        public ChunkResult() {
            this.metadata = new java.util.HashMap<>();
        }
        
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
        
        public String getContentType() {
            return contentType;
        }
        
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
        
        public java.util.Map<String, String> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(java.util.Map<String, String> metadata) {
            this.metadata = metadata;
        }
        
        public void addMetadata(String key, String value) {
            this.metadata.put(key, value);
        }
    }
}
