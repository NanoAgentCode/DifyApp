package com.github.app.dify.resp;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 智能问答响应
 */
@Schema(description = "智能问答响应")
public class ChatResponse {
    
    @Schema(description = "答案")
    private String answer;
    
    @Schema(description = "是否完成（流式响应时使用）")
    private Boolean finished;
    
    @Schema(description = "会话ID")
    private Long conversationId;
    
    public String getAnswer() {
        return answer;
    }
    
    public void setAnswer(String answer) {
        this.answer = answer;
    }
    
    public Boolean getFinished() {
        return finished;
    }
    
    public void setFinished(Boolean finished) {
        this.finished = finished;
    }
    
    public Long getConversationId() {
        return conversationId;
    }
    
    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }
}