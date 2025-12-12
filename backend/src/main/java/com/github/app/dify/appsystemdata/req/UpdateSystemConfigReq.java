package com.github.app.dify.appsystemdata.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新系统配置请求
 */
@Schema(description = "更新系统配置请求")
public class UpdateSystemConfigReq {
    
    @NotBlank(message = "配置键不能为空")
    @Size(max = 100, message = "配置键长度不能超过100")
    @Schema(description = "配置键")
    private String configKey;
    
    @Schema(description = "配置值（JSON格式）")
    private String configValue;
    
    @Size(max = 500, message = "配置描述长度不能超过500")
    @Schema(description = "配置描述")
    private String description;
    
    @Size(max = 50, message = "配置分组长度不能超过50")
    @Schema(description = "配置分组")
    private String configGroup;
    
    @Size(max = 20, message = "配置类型长度不能超过20")
    @Schema(description = "配置类型")
    private String configType;
    
    // Getters and Setters
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
}

