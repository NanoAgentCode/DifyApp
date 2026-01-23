package com.github.app.dify.knowledgebase.req;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 知识库导入请求
 */
@Schema(description = "知识库导入请求")
public class KnowledgeBaseImportRequest {
    
    @Schema(description = "知识库名称（必填）")
    private String knowledgeBaseName;
    
    @Schema(description = "知识库描述（可选）")
    private String description;
    
    @Schema(description = "向量存储类型（可选，默认系统默认值）")
    private String vectorStoreType;
    
    @Schema(description = "是否公开（可选，默认false）")
    private Boolean isPublic;
    
    @Schema(description = "Top-K检索数量（可选，默认系统全局配置）")
    private Integer topK;
    
    @Schema(description = "向量化模型ID（可选，默认系统默认模型）")
    private Long embeddingModelId;
    
    // Getters and Setters
    public String getKnowledgeBaseName() {
        return knowledgeBaseName;
    }
    
    public void setKnowledgeBaseName(String knowledgeBaseName) {
        this.knowledgeBaseName = knowledgeBaseName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getVectorStoreType() {
        return vectorStoreType;
    }
    
    public void setVectorStoreType(String vectorStoreType) {
        this.vectorStoreType = vectorStoreType;
    }
    
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public Integer getTopK() {
        return topK;
    }
    
    public void setTopK(Integer topK) {
        this.topK = topK;
    }
    
    public Long getEmbeddingModelId() {
        return embeddingModelId;
    }
    
    public void setEmbeddingModelId(Long embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }
}
