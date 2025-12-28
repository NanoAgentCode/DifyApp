package com.github.app.dify.model.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
/**
 * 模型配置请求
 */
@Schema(description = "模型配置请求")
public class ModelConfigRequest {
    
    @Schema(description = "操作类型：add-添加, update-更新, delete-删除, setDefault-设置默认, toggleEnabled-切换启用状态")
    @NotBlank(message = "操作类型不能为空")
    private String action;
    
    @Schema(description = "模型类型：qa-问答模型, embedding-向量化模型")
    private String type;
    
    @Schema(description = "模型ID（更新、删除、设置默认、切换状态时需要）")
    private Long modelId;
    
    @Schema(description = "模型信息（添加、更新时需要）")
    private ModelInfo model;
    
    @Schema(description = "启用状态（切换状态时需要）")
    private Boolean enabled;
    
    @Schema(description = "使用场景（设置默认时需要，qa模型专用：chat/rag/both）")
    private String useFor;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public ModelInfo getModel() {
        return model;
    }

    public void setModel(ModelInfo model) {
        this.model = model;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getUseFor() {
        return useFor;
    }

    public void setUseFor(String useFor) {
        this.useFor = useFor;
    }

    /**
     * 模型信息
     */
    @Schema(description = "模型信息")
    public static class ModelInfo {
        @Schema(description = "模型ID（更新时需要）")
        private Long id;
        
        @Schema(description = "模型名称")
        @NotBlank(message = "模型名称不能为空")
        private String name;
        
        @Schema(description = "提供商类型：openai, vllm, ollama")
        @NotBlank(message = "提供商类型不能为空")
        private String provider;
        
        @Schema(description = "提供商类型（原始值，用于前端显示）")
        private String providerType;
        
        @Schema(description = "API 地址")
        @NotBlank(message = "API 地址不能为空")
        private String apiUrl;
        
        @Schema(description = "API Key")
        private String apiKey;
        
        @Schema(description = "模型标识")
        @NotBlank(message = "模型标识不能为空")
        private String model;
        
        @Schema(description = "使用场景（qa模型专用）：chat-仅智能问答, rag-仅知识库问答, both-两者都使用")
        private String useFor;
        
        @Schema(description = "超时时间（毫秒，embedding模型专用）")
        private Integer timeout;
        
        @Schema(description = "批处理大小（embedding模型专用）")
        private Integer batchSize;
        
        @Schema(description = "是否启用")
        private Boolean enabled;
        
        @Schema(description = "是否支持多模态（qa模型专用）")
        private Boolean supportsMultimodal;
        
        @Schema(description = "是否支持视觉输入（qa模型专用）")
        private Boolean supportsVision;

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

        public Boolean getSupportsMultimodal() {
            return supportsMultimodal;
        }

        public void setSupportsMultimodal(Boolean supportsMultimodal) {
            this.supportsMultimodal = supportsMultimodal;
        }

        public Boolean getSupportsVision() {
            return supportsVision;
        }

        public void setSupportsVision(Boolean supportsVision) {
            this.supportsVision = supportsVision;
        }
    }
}

