package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;

/**
 * 测试向量数据库连接请求
 */
@ApiModel("测试向量数据库连接请求")
public class TestVectorDatabaseConnectionRequest {
    
    @ApiModelProperty("数据库类型：qdrant, milvus, faiss")
    @NotBlank(message = "数据库类型不能为空")
    private String type;
    
    @ApiModelProperty("连接地址（URL或路径）")
    @NotBlank(message = "连接地址不能为空")
    private String url;
    
    @ApiModelProperty("API Key（可选）")
    private String apiKey;
    
    @ApiModelProperty("超时时间（毫秒）")
    private Integer timeout;

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
}

