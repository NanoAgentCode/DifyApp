package com.github.app.dify.documentreader.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 文档问答响应
 */
@Schema(description = "文档问答响应")
public class DocumentQAResponse {
    
    @Schema(description = "答案")
    private String answer;
    
    @Schema(description = "对话ID")
    private String conversationId;
    
    @Schema(description = "来源文档片段")
    private List<SourceDocument> sources;
    
    @Schema(description = "是否完成（流式响应）")
    private Boolean finished;
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public String getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public List<SourceDocument> getSources() {
        return sources;
    }
    
    public void setSources(List<SourceDocument> sources) {
        this.sources = sources;
    }
    
    public Boolean getFinished() {
        return finished;
    }
    
    public void setFinished(Boolean finished) {
        this.finished = finished;
    }
    
    /**
     * 来源文档片段
     */
    public static class SourceDocument {
        private Long documentId;
        private Integer chunkIndex;
        private String text;
        private Double score;
        
        public Long getDocumentId() {
            return documentId;
        }
        
        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
        
        public Integer getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public Double getScore() {
            return score;
        }
        
        public void setScore(Double score) {
            this.score = score;
        }
    }
}

