package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户知识库可见性响应
 */
@ApiModel("用户知识库可见性响应")
public class UserKnowledgeBaseVisibilityResp {
    
    @ApiModelProperty("知识库ID")
    private Long knowledgeBaseId;
    
    @ApiModelProperty("知识库名称")
    private String knowledgeBaseName;
    
    @ApiModelProperty("知识库描述")
    private String knowledgeBaseDescription;
    
    @ApiModelProperty("知识库状态：1-启用，0-禁用")
    private Integer knowledgeBaseStatus;
    
    @ApiModelProperty("是否可见")
    private Boolean visible;
    
    // Getters and Setters
    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }
    
    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
    
    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }
    
    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }
    
    public String getKnowledgeBaseDescription() {
        return knowledgeBaseDescription;
    }
    
    public void setKnowledgeBaseDescription(String knowledgeBaseDescription) {
        this.knowledgeBaseDescription = knowledgeBaseDescription;
    }
    
    public Integer getKnowledgeBaseStatus() {
        return knowledgeBaseStatus;
    }
    
    public void setKnowledgeBaseStatus(Integer knowledgeBaseStatus) {
        this.knowledgeBaseStatus = knowledgeBaseStatus;
    }
    
    public Boolean getVisible() {
        return visible;
    }
    
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
}

