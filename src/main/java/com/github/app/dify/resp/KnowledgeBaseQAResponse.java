package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * 知识库问答响应
 */
@ApiModel("知识库问答响应")
public class KnowledgeBaseQAResponse {
    
    @ApiModelProperty("答案")
    private String answer;
    
    @ApiModelProperty("对话ID")
    private String conversationId;
    
    @ApiModelProperty("来源文档")
    private List<SourceDocument> sources;
    
    @ApiModelProperty("是否完成（流式响应）")
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
     * 来源文档
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

