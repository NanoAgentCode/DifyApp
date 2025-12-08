package com.github.app.dify.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
/**
 * 更新知识库请求
 */
@Schema(description = "更新知识库请求")
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateKnowledgeBaseReq {
    
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @Schema(description = "知识库名称")
    private String name;
    
    @Size(max = 500, message = "知识库描述长度不能超过500")
    @Schema(description = "知识库描述")
    private String description;
    
    @Schema(description = "知识库状态：1-启用，0-禁用")
    private Integer status;
    
    @Schema(description = "是否公开：true-公开，false-私有")
    private Boolean isPublic;
    
    @Schema(description = "向量化模型ID（可选，如果不指定则使用默认向量化模型）")
    private Long embeddingModelId;
    
    @Schema(description = "Top-K检索数量（可选，如果不指定则使用全局配置）")
    private Integer topK;
    
    @Schema(description = "向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库")
    private String vectorStoreType;
    
    @Schema(description = "向量库实例ID（关联VECTOR_DATABASE表的id，可选，如果指定则使用该实例）")
    private Long vectorDatabaseId;
    
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
}