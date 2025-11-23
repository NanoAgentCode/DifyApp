package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 创建会话请求
 */
@ApiModel("创建会话请求")
public class CreateConversationRequest {
    
    @ApiModelProperty("会话标题（可选，不提供则自动生成）")
    private String title;
    
    @ApiModelProperty("应用ID（可选）")
    private Long appId;
    
    @ApiModelProperty("知识库ID（可选）")
    private Long knowledgeBaseId;
    
    @ApiModelProperty("会话类型：1-普通聊天，2-知识库问答")
    private Integer type;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
}

