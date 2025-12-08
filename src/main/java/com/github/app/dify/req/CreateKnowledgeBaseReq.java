package com.github.app.dify.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 创建知识库请求
 */
@Schema(description = "创建知识库请求")
public class CreateKnowledgeBaseReq {
    
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @Schema(description = "知识库名称")
    private String name;
    
    @Size(max = 500, message = "知识库描述长度不能超过500")
    @Schema(description = "知识库描述")
    private String description;
    
    @Schema(description = "知识库状态：1-启用，0-禁用，默认为1")
    private Integer status;
    
    @Schema(description = "是否公开：true-公开，false-私有，默认为false（私有）")
    private Boolean isPublic;
    
    @Schema(description = "向量化模型ID（可选，如果不指定则使用默认向量化模型）")
    private Long embeddingModelId;
    
    @Schema(description = "Top-K检索数量（可选，如果不指定则使用全局配置）")
    private Integer topK;
    
    @Schema(description = "向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库（默认为qdrant）")
    private String vectorStoreType;
    
    @Schema(description = "向量库实例ID（关联VECTOR_DATABASE表的id，可选，如果指定则使用该实例，否则使用默认实例）")
    private Long vectorDatabaseId;
    
    @Schema(description = "租户编号")
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
    
    public Long getVectorDatabaseId() {
        return vectorDatabaseId;
    }
    
    public void setVectorDatabaseId(Long vectorDatabaseId) {
        this.vectorDatabaseId = vectorDatabaseId;
    }
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}