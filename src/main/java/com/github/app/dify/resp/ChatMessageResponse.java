package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 消息详情响应（会话中的单条消息）
 */
@ApiModel("消息详情响应")
public class ChatMessageResponse {
    
    @ApiModelProperty("消息ID")
    private Long id;
    
    @ApiModelProperty("会话ID")
    private Long conversationId;
    
    @ApiModelProperty("角色（user/assistant）")
    private String role;
    
    @ApiModelProperty("消息内容")
    private String content;
    
    @ApiModelProperty("消息顺序")
    private Integer sequence;
    
    @ApiModelProperty("创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}

