package com.github.app.dify.knowledgebase.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
 * 知识库表
 * @TableName KNOWLEDGE_BASE
 */
@Entity
@Table(name = "KNOWLEDGE_BASE")
public class KnowledgeBase extends BaseSoftDeleteEntity implements Serializable {

    /**
     * 知识库编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "知识库编号")
    private Long id;
    
    /**
     * 知识库名称
     */
    @NotBlank(message="[知识库名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "知识库名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "name", columnDefinition = "VARCHAR(100)")
    private String name;
    
    /**
     * 知识库描述
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "知识库描述")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "description", columnDefinition = "VARCHAR(500)")
    private String description;
    
    /**
     * 知识库状态：1-启用，0-禁用
     */
    @Schema(description = "知识库状态：1-启用，0-禁用")
    private Integer status;
    
    /**
     * 创建者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
    
    /**
     * 创建者ID
     */
    @Schema(description = "创建者ID")
    @Column(name = "creator_id")
    private Long creatorId;
    
    /**
     * 是否公开：true-公开，false-私有
     */
    @Schema(description = "是否公开：true-公开，false-私有")
    @Column(name = "is_public")
    private Boolean isPublic;
    
    /**
     * 向量化模型ID
     */
    @Schema(description = "向量化模型ID")
    @Column(name = "embedding_model_id")
    private Long embeddingModelId;
    
    /**
     * Top-K检索数量（每个知识库可单独配置，如果为null则使用全局配置）
     */
    @Schema(description = "Top-K检索数量（每个知识库可单独配置，如果为null则使用全局配置）")
    @Column(name = "top_k")
    private Integer topK;
    
    /**
     * 向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库
     */
    @Schema(description = "向量存储类型：qdrant-Qdrant向量数据库，faiss-FAISS本地文件存储，milvus-Milvus向量数据库")
    @Column(name = "vector_store_type", columnDefinition = "VARCHAR(20)")
    private String vectorStoreType;
    
    /**
     * 向量库实例ID（关联VECTOR_DATABASE表的id）
     */
    @Schema(description = "向量库实例ID（关联VECTOR_DATABASE表的id）")
    @Column(name = "vector_database_id")
    private Long vectorDatabaseId;
    
    /**
     * 更新者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "更新者")
    @Length(max= 64,message="编码长度不能超过64")
    private String updater;
    
    /**
     * 租户编号
     */
    @Schema(description = "租户编号")
    private Integer tenantId;
    
    /**
     * 知识库智能摘要
     */
    @Size(max= 2000,message="编码长度不能超过2000")
    @Schema(description = "知识库智能摘要")
    @Length(max= 2000,message="编码长度不能超过2000")
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

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
    
    public Long getVectorDatabaseId() {
        return vectorDatabaseId;
    }
    
    public void setVectorDatabaseId(Long vectorDatabaseId) {
        this.vectorDatabaseId = vectorDatabaseId;
    }
    
    public String getUpdater() {
        return updater;
    }

    public void setUpdater(String updater) {
        this.updater = updater;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
}
