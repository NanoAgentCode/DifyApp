package com.github.app.dify.appknowledgebase.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
/**
 * 测试向量数据库连接请求
 */
@Schema(description = "测试向量数据库连接请求")
public class TestVectorDatabaseConnectionRequest {
    
    @Schema(description = "数据库类型：qdrant, milvus, faiss, chroma, weaviate, elasticsearch")
    @NotBlank(message = "数据库类型不能为空")
    private String type;
    
    @Schema(description = "连接地址（URL或路径）")
    @NotBlank(message = "连接地址不能为空")
    private String url;
    
    @Schema(description = "API Key（可选）")
    private String apiKey;
    
    @Schema(description = "额外配置（JSON格式，可包含username和password）")
    private String extraConfig;
    
    @Schema(description = "超时时间（毫秒）")
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
    
    public String getExtraConfig() {
        return extraConfig;
    }
    
    public void setExtraConfig(String extraConfig) {
        this.extraConfig = extraConfig;
    }
    
    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }
}