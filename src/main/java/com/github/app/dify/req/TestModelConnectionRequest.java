package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;

/**
 * 测试模型连接请求
 */
@ApiModel("测试模型连接请求")
public class TestModelConnectionRequest {
    
    @ApiModelProperty("模型类型：qa-问答模型, embedding-向量化模型")
    @NotBlank(message = "模型类型不能为空")
    private String type;
    
    @ApiModelProperty("提供商类型：openai, vllm, ollama")
    @NotBlank(message = "提供商类型不能为空")
    private String provider;
    
    @ApiModelProperty("提供商类型（原始值）")
    private String providerType;
    
    @ApiModelProperty("API 地址")
    @NotBlank(message = "API 地址不能为空")
    private String apiUrl;
    
    @ApiModelProperty("API Key")
    private String apiKey;
    
    @ApiModelProperty("模型标识")
    @NotBlank(message = "模型标识不能为空")
    private String model;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
}

