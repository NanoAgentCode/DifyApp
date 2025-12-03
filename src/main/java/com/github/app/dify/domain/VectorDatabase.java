package com.github.app.dify.domain;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.Length;

/**
 * 向量数据库配置表
 * @TableName VECTOR_DATABASE
 */
@Entity
@Table(name = "VECTOR_DATABASE")
public class VectorDatabase implements Serializable {

    /**
     * 配置编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("配置编号")
    private Long id;
    
    /**
     * 配置名称
     */
    @NotBlank(message="[配置名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @ApiModelProperty("配置名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "name", columnDefinition = "VARCHAR(100)")
    private String name;
    
    /**
     * 数据库类型：qdrant, milvus, milvus-lite, faiss
     */
    @NotBlank(message="[数据库类型]不能为空")
    @Size(max= 20,message="编码长度不能超过20")
    @ApiModelProperty("数据库类型：qdrant, milvus, faiss")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "type", columnDefinition = "VARCHAR(20)")
    private String type;
    
    /**
     * 连接地址（URL或路径）
     */
    @NotBlank(message="[连接地址]不能为空")
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("连接地址（URL或路径）")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "url", columnDefinition = "VARCHAR(500)")
    private String url;
    
    /**
     * API Key（可选）
     */
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("API Key（可选）")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "api_key", columnDefinition = "VARCHAR(500)")
    private String apiKey;
    
    /**
     * 超时时间（毫秒）
     */
    @ApiModelProperty("超时时间（毫秒）")
    @Column(name = "timeout")
    private Integer timeout;
    
    /**
     * 额外配置（JSON格式）
     */
    @Size(max= 2000,message="编码长度不能超过2000")
    @ApiModelProperty("额外配置（JSON格式）")
    @Length(max= 2000,message="编码长度不能超过2000")
    @Column(name = "extra_config", columnDefinition = "TEXT")
    private String extraConfig;
    
    /**
     * 是否启用：true-启用, false-禁用
     */
    @ApiModelProperty("是否启用：true-启用, false-禁用")
    @Column(name = "enabled")
    private Boolean enabled;
    
    /**
     * 是否默认：true-默认, false-非默认
     */
    @ApiModelProperty("是否默认：true-默认, false-非默认")
    @Column(name = "is_default")
    private Boolean isDefault;
    
    /**
     * 描述
     */
    @Size(max= 500,message="编码长度不能超过500")
    @ApiModelProperty("描述")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "description", columnDefinition = "VARCHAR(500)")
    private String description;
    
    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @ApiModelProperty("更新时间")
    @Column(name = "update_time")
    private Date updateTime;
    
    /**
     * 是否删除：0-未删除，1-已删除
     */
    @ApiModelProperty("是否删除：0-未删除，1-已删除")
    @Column(name = "deleted")
    private Integer deleted;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public String getExtraConfig() {
        return extraConfig;
    }

    public void setExtraConfig(String extraConfig) {
        this.extraConfig = extraConfig;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}

