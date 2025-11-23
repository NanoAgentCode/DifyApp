package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 智能问答响应
 */
@ApiModel("智能问答响应")
public class ChatResponse {
    
    @ApiModelProperty("答案")
    private String answer;
    
    @ApiModelProperty("是否完成（流式响应时使用）")
    private Boolean finished;
    
    @ApiModelProperty("会话ID")
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

