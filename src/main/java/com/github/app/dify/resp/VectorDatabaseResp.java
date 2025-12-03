package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 向量数据库配置响应
 */
@ApiModel("向量数据库配置响应")
public class VectorDatabaseResp {
    
    @ApiModelProperty("配置编号")
    private Long id;
    
    @ApiModelProperty("配置名称")
    private String name;
    
    @ApiModelProperty("数据库类型：qdrant, milvus, milvus-lite, faiss")
    private String type;
    
    @ApiModelProperty("连接地址（URL或路径）")
    private String url;
    
    @ApiModelProperty("API Key（可选，返回时隐藏敏感信息）")
    private String apiKey;
    
    @ApiModelProperty("超时时间（毫秒）")
    private Integer timeout;
    
    @ApiModelProperty("额外配置（JSON格式）")
    private String extraConfig;
    
    @ApiModelProperty("是否启用")
    private Boolean enabled;
    
    @ApiModelProperty("是否默认")
    private Boolean isDefault;
    
    @ApiModelProperty("描述")
    private String description;
    
    @ApiModelProperty("创建时间")
    private Date createTime;
    
    @ApiModelProperty("更新时间")
    private Date updateTime;

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
}

