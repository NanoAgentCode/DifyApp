package com.github.app.dify.appknowledgebase.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
/**
 * 问答模型响应
 */
@Schema(description = "问答模型响应")
public class QAModelResp {
    
    @Schema(description = "模型编号")
    private Long id;
    
    @Schema(description = "模型名称")
    private String name;
    
    @Schema(description = "提供商类型")
    private String provider;
    
    @Schema(description = "提供商类型（原始值）")
    private String providerType;
    
    @Schema(description = "API 地址")
    private String apiUrl;
    
    @Schema(description = "API Key")
    private String apiKey;
    
    @Schema(description = "模型标识")
    private String model;
    
    @Schema(description = "使用场景：chat-仅智能问答, rag-仅知识库问答, both-两者都使用")
    private String useFor;
    
    @Schema(description = "是否启用")
    private Boolean enabled;
    
    @Schema(description = "是否默认")
    private Boolean isDefault;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
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

    public String getUseFor() {
        return useFor;
    }

    public void setUseFor(String useFor) {
        this.useFor = useFor;
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