package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 知识库响应
 */
@ApiModel("知识库响应")
public class KnowledgeBaseResp {
    
    @ApiModelProperty("知识库编号")
    private Long id;
    
    @ApiModelProperty("知识库名称")
    private String name;
    
    @ApiModelProperty("知识库描述")
    private String description;
    
    @ApiModelProperty("知识库状态：1-启用，0-禁用")
    private Integer status;
    
    @ApiModelProperty("文档数量")
    private Integer documentCount;
    
    @ApiModelProperty("成功向量化的文档数量")
    private Integer successDocumentCount;
    
    @ApiModelProperty("向量化失败的文档数量")
    private Integer failedDocumentCount;
    
    @ApiModelProperty("创建者")
    private String creator;
    
    @ApiModelProperty("创建者ID")
    private Long creatorId;
    
    @ApiModelProperty("是否公开：true-公开，false-私有")
    private Boolean isPublic;
    
    @ApiModelProperty("向量化模型ID")
    private Long embeddingModelId;
    
    @ApiModelProperty("Top-K检索数量（如果为null则使用全局配置）")
    private Integer topK;
    
    @ApiModelProperty("向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库，milvus-lite-Milvus Lite轻量级版本")
    private String vectorStoreType;
    
    @ApiModelProperty("创建时间")
    private Date createTime;
    
    @ApiModelProperty("更新者")
    private String updater;
    
    @ApiModelProperty("更新时间")
    private Date updateTime;
    
    @ApiModelProperty("租户编号")
    private Integer tenantId;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public Integer getDocumentCount() {
        return documentCount;
    }
    
    public void setDocumentCount(Integer documentCount) {
        this.documentCount = documentCount;
    }
    
    public Integer getSuccessDocumentCount() {
        return successDocumentCount;
    }
    
    public void setSuccessDocumentCount(Integer successDocumentCount) {
        this.successDocumentCount = successDocumentCount;
    }
    
    public Integer getFailedDocumentCount() {
        return failedDocumentCount;
    }
    
    public void setFailedDocumentCount(Integer failedDocumentCount) {
        this.failedDocumentCount = failedDocumentCount;
    }
    
    public String getCreator() {
        return creator;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public Long getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
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
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    public String getUpdater() {
        return updater;
    }
    
    public void setUpdater(String updater) {
        this.updater = updater;
    }
    
    public Date getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}

