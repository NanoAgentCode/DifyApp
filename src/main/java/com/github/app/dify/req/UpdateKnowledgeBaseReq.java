package com.github.app.dify.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Size;

/**
 * 更新知识库请求
 */
@ApiModel("更新知识库请求")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateKnowledgeBaseReq {
    
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @ApiModelProperty("知识库名称")
    private String name;
    
    @Size(max = 500, message = "知识库描述长度不能超过500")
    @ApiModelProperty("知识库描述")
    private String description;
    
    @ApiModelProperty("知识库状态：1-启用，0-禁用")
    private Integer status;
    
    @ApiModelProperty("是否公开：true-公开，false-私有")
    private Boolean isPublic;
    
    @ApiModelProperty("向量化模型ID（可选，如果不指定则使用默认向量化模型）")
    private Long embeddingModelId;
    
    // Getters and Setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public Long getEmbeddingModelId() {
        return embeddingModelId;
    }
    
    public void setEmbeddingModelId(Long embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }
}

