package com.github.app.dify.domain;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.Length;

/**
 * 知识库表
 * @TableName KNOWLEDGE_BASE
 */
@Entity
@Table(name = "KNOWLEDGE_BASE")
public class KnowledgeBase implements Serializable {

    /**
     * 知识库编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("知识库编号")
    private Long id;
    
    /**
     * 知识库名称
     */
    @NotBlank(message="[知识库名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @ApiModelProperty("知识库名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "name", columnDefinition = "VARCHAR(100)")
    private String name;
    
    /**
     * 知识库描述
     */
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("知识库描述")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "description", columnDefinition = "VARCHAR(500)")
    private String description;
    
    /**
     * 知识库状态：1-启用，0-禁用
     */
    @ApiModelProperty("知识库状态：1-启用，0-禁用")
    private Integer status;
    
    /**
     * 创建者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
    
    /**
     * 创建者ID
     */
    @ApiModelProperty("创建者ID")
    @Column(name = "creator_id")
    private Long creatorId;
    
    /**
     * 是否公开：true-公开，false-私有
     */
    @ApiModelProperty("是否公开：true-公开，false-私有")
    @Column(name = "is_public")
    private Boolean isPublic;
    
    /**
     * 向量化模型ID
     */
    @ApiModelProperty("向量化模型ID")
    @Column(name = "embedding_model_id")
    private Long embeddingModelId;
    
    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private Date createTime;
    
    /**
     * 更新者
     */
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("更新者")
    @Length(max= 64,message="编码长度不能超过64")
    private String updater;
    
    /**
     * 更新时间
     */
    @ApiModelProperty("更新时间")
    private Date updateTime;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @ApiModelProperty("是否删除：0-未删除，1-已删除")
    private Integer deleted;
    
    /**
     * 租户编号
     */
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}

