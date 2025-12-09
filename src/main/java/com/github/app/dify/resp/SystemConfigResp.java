package com.github.app.dify.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

/**
 * 系统配置响应
 */
@Schema(description = "系统配置响应")
public class SystemConfigResp {
    
    @Schema(description = "配置编号")
    private Long id;
    
    @Schema(description = "配置键")
    private String configKey;
    
    @Schema(description = "配置值（JSON格式）")
    private String configValue;
    
    @Schema(description = "配置描述")
    private String description;
    
    @Schema(description = "配置分组")
    private String configGroup;
    
    @Schema(description = "配置类型")
    private String configType;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
    @Schema(description = "创建者")
    private String creator;
    
    @Schema(description = "创建者ID")
    private Long creatorId;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public String getCreator() {
        return creator;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public Long getCreatorId() {
        return creatorId;
    }
    
    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}

