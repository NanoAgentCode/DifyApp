package com.github.app.dify.appsystemdata.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * 系统配置表（通用配置存储）
 * @TableName SYSTEM_CONFIG
 */
@Entity
@Table(name = "SYSTEM_CONFIG", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"config_key"})
})
public class SystemConfig implements Serializable {

    /**
     * 配置编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "配置编号")
    private Long id;
    
    /**
     * 配置键（唯一，如：help.knowledgeBaseId, help.modelId）
     */
    @NotBlank(message="[配置键]不能为空")
    @Size(max= 100,message="配置键长度不能超过100")
    @Schema(description = "配置键（唯一）")
    @Length(max= 100,message="配置键长度不能超过100")
    @Column(name = "config_key", unique = true, columnDefinition = "VARCHAR(100)")
    private String configKey;
    
    /**
     * 配置值（JSON格式，支持复杂类型）
     */
    @Schema(description = "配置值（JSON格式）")
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;
    
    /**
     * 配置描述
     */
    @Size(max= 500,message="配置描述长度不能超过500")
    @Schema(description = "配置描述")
    @Length(max= 500,message="配置描述长度不能超过500")
    @Column(name = "description", columnDefinition = "VARCHAR(500)")
    private String description;
    
    /**
     * 配置分组（如：help-帮助配置，system-系统配置）
     */
    @Size(max= 50,message="配置分组长度不能超过50")
    @Schema(description = "配置分组")
    @Length(max= 50,message="配置分组长度不能超过50")
    @Column(name = "config_group", columnDefinition = "VARCHAR(50)")
    private String configGroup;
    
    /**
     * 配置类型（如：number, string, boolean, json）
     */
    @Size(max= 20,message="配置类型长度不能超过20")
    @Schema(description = "配置类型")
    @Length(max= 20,message="配置类型长度不能超过20")
    @Column(name = "config_type", columnDefinition = "VARCHAR(20)")
    private String configType;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;
    
    /**
     * 创建者
     */
    @Size(max= 64,message="创建者长度不能超过64")
    @Schema(description = "创建者")
    @Length(max= 64,message="创建者长度不能超过64")
    @Column(name = "creator", columnDefinition = "VARCHAR(64)")
    private String creator;
    
    /**
     * 创建者ID
     */
    @Schema(description = "创建者ID")
    @Column(name = "creator_id")
    private Long creatorId;
    
    /**
     * 是否删除（0-未删除，1-已删除）
     */
    @Schema(description = "是否删除（0-未删除，1-已删除）")
    @Column(name = "deleted")
    private Integer deleted;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getConfigType() {
        return configType;
    }

    public void setConfigType(String configType) {
        this.configType = configType;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

