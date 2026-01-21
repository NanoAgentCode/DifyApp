package com.github.app.dify.knowledgebase.service.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容结构
 * 表示文档中不同类型的内容片段
 */
public class ContentStructure {
    
    private List<ContentSegment> segments;
    
    public ContentStructure() {
        this.segments = new ArrayList<>();
    }
    
    public List<ContentSegment> getSegments() {
        return segments;
    }
    
    public void setSegments(List<ContentSegment> segments) {
        this.segments = segments;
    }
    
    public void addSegment(ContentSegment segment) {
        this.segments.add(segment);
    }
    
    /**
     * 内容片段
     */
    public static class ContentSegment {
        private String type; // 内容类型：text, table, code, heading
        private String content; // 内容文本
        private int startIndex; // 在原文档中的起始位置
        private int endIndex; // 在原文档中的结束位置
        private java.util.Map<String, String> metadata; // 额外元数据
        
        public ContentSegment() {
            this.metadata = new java.util.HashMap<>();
        }
        
        public ContentSegment(String type, String content, int startIndex, int endIndex) {
            this();
            this.type = type;
            this.content = content;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
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
    
    /**
     * 内容类型常量
     */
    public static class ContentType {
        public static final String TEXT = "text";
        public static final String TABLE = "table";
        public static final String CODE = "code";
        public static final String HEADING = "heading";
    }
}
