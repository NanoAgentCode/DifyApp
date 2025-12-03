package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建知识库请求
 */
@ApiModel("创建知识库请求")
public class CreateKnowledgeBaseReq {
    
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @ApiModelProperty(value = "知识库名称", required = true)
    private String name;
    
    @Size(max = 500, message = "知识库描述长度不能超过500")
    @ApiModelProperty("知识库描述")
    private String description;
    
    @ApiModelProperty("知识库状态：1-启用，0-禁用，默认为1")
    private Integer status;
    
    @ApiModelProperty("是否公开：true-公开，false-私有，默认为false（私有）")
    private Boolean isPublic;
    
    @ApiModelProperty("向量化模型ID（可选，如果不指定则使用默认向量化模型）")
    private Long embeddingModelId;
    
    @ApiModelProperty("Top-K检索数量（可选，如果不指定则使用全局配置）")
    private Integer topK;
    
    @ApiModelProperty("向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库（默认为qdrant）")
    private String vectorStoreType;
    
    @ApiModelProperty("租户编号")
    private Integer tenantId;
    
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
    
    public Integer getTopK() {
        return topK;
    }
    
    public void setTopK(Integer topK) {
        this.topK = topK;
    }
    
    public String getVectorStoreType() {
        return vectorStoreType;
    }
    
    public void setVectorStoreType(String vectorStoreType) {
        this.vectorStoreType = vectorStoreType;
    }
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}

