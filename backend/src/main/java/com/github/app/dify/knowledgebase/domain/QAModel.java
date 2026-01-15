package com.github.app.dify.knowledgebase.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
 * 问答模型表
 * @TableName QA_MODEL
 */
@Entity
@Table(name = "QA_MODEL")
public class QAModel extends BaseSoftDeleteEntity {

    /**
     * 模型编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "模型编号")
    private Long id;
    
    /**
     * 模型名称
     */
    @NotBlank(message="[模型名称]不能为空")
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "模型名称")
    @Length(max= 100,message="编码长度不能超过100")
    @Column(name = "name", columnDefinition = "VARCHAR(100)")
    private String name;
    
    /**
     * 提供商类型：openai, vllm, ollama
     */
    @NotBlank(message="[提供商类型]不能为空")
    @Size(max= 20,message="编码长度不能超过20")
    @Schema(description = "提供商类型：openai, vllm, ollama")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "provider", columnDefinition = "VARCHAR(20)")
    private String provider;
    
    /**
     * 提供商类型（原始值，用于前端显示）
     */
    @Size(max= 20,message="编码长度不能超过20")
    @Schema(description = "提供商类型（原始值）")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "provider_type", columnDefinition = "VARCHAR(20)")
    private String providerType;
    
    /**
     * API 地址
     */
    @NotBlank(message="[API 地址]不能为空")
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "API 地址")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "api_url", columnDefinition = "VARCHAR(500)")
    private String apiUrl;
    
    /**
     * API Key
     */
    @Size(max= 500,message="编码长度不能超过500")
    @Schema(description = "API Key")
    @Length(max= 500,message="编码长度不能超过500")
    @Column(name = "api_key", columnDefinition = "VARCHAR(500)")
    private String apiKey;
    
    /**
     * 模型标识
     */
    @NotBlank(message="[模型标识]不能为空")
    @Size(max= 200,message="编码长度不能超过200")
    @Schema(description = "模型标识")
    @Length(max= 200,message="编码长度不能超过200")
    @Column(name = "model", columnDefinition = "VARCHAR(200)")
    private String model;
    
    /**
     * 使用场景：chat-仅智能问答, rag-仅知识库问答, both-两者都使用
     */
    @NotBlank(message="[使用场景]不能为空")
    @Size(max= 20,message="编码长度不能超过20")
    @Schema(description = "使用场景：chat-仅智能问答, rag-仅知识库问答, both-两者都使用")
    @Length(max= 20,message="编码长度不能超过20")
    @Column(name = "use_for", columnDefinition = "VARCHAR(20)")
    private String useFor;
    
    /**
     * 是否启用：true-启用, false-禁用
     */
    @Schema(description = "是否启用：true-启用, false-禁用")
    @Column(name = "enabled")
    private Boolean enabled;
    
    /**
     * 是否默认：true-默认, false-非默认
     */
    @Schema(description = "是否默认：true-默认, false-非默认")
    @Column(name = "is_default")
    private Boolean isDefault;
    
    /**
     * 是否支持多模态：true-支持, false-不支持
     */
    @Schema(description = "是否支持多模态：true-支持, false-不支持")
    @Column(name = "supports_multimodal")
    private Boolean supportsMultimodal;
    
    /**
     * 是否支持视觉输入：true-支持, false-不支持
     */
    @Schema(description = "是否支持视觉输入：true-支持, false-不支持")
    @Column(name = "supports_vision")
    private Boolean supportsVision;
    
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
