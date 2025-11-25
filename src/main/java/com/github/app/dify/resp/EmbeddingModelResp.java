package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 向量化模型响应
 */
@ApiModel("向量化模型响应")
public class EmbeddingModelResp {
    
    @ApiModelProperty("模型编号")
    private Long id;
    
    @ApiModelProperty("模型名称")
    private String name;
    
    @ApiModelProperty("提供商类型")
    private String provider;
    
    @ApiModelProperty("提供商类型（原始值）")
    private String providerType;
    
    @ApiModelProperty("API 地址")
    private String apiUrl;
    
    @ApiModelProperty("API Key")
    private String apiKey;
    
    @ApiModelProperty("模型标识")
    private String model;
    
    @ApiModelProperty("超时时间（毫秒）")
    private Integer timeout;
    
    @ApiModelProperty("批处理大小")
    private Integer batchSize;
    
    @ApiModelProperty("是否启用")
    private Boolean enabled;
    
    @ApiModelProperty("是否默认")
    private Boolean isDefault;
    
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
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

