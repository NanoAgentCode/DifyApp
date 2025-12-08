package com.github.app.dify.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
/**
 * AI应用响应
 */
@Schema(description = "AI应用响应")
public class AiAppResp {
    
    @Schema(description = "应用编号")
    private Long id;
    
    @Schema(description = "应用名称")
    private String name;
    
    @Schema(description = "应用描述")
    private String description;
    
    @Schema(description = "应用类型：1-chatFlow，2-workflow")
    private Integer type;
    
    @Schema(description = "应用状态")
    private Integer status;
    
    @Schema(description = "应用配置")
    private String inputs;
    
    @Schema(description = "应用图标")
    private String icon;
    
    @Schema(description = "排序")
    private Integer sort;
    
    @Schema(description = "创建者")
    private String creator;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新者")
    private String updater;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
    @Schema(description = "租户编号")
    private Integer tenantId;
    
    @Schema(description = "Dify API Key")
    private String appId;
    
    @Schema(description = "Dify API Base URL")
    private String apiBaseUrl;
    
    @Schema(description = "是否支持流式响应")
    private Boolean streamEnabled;
    
    @Schema(description = "是否需要上传文件")
    private Boolean fileUploadEnabled;
    
    @Schema(description = "是否显示文本输入框")
    private Boolean inputEnabled;
    
    @Schema(description = "主题色")
    private String themeColor;
    
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Integer getType() {
        return type;
    }
    
    public void setType(Integer type) {
        this.type = type;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public String getInputs() {
        return inputs;
    }
    
    public void setInputs(String inputs) {
        this.inputs = inputs;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public Integer getSort() {
        return sort;
    }
    
    public void setSort(Integer sort) {
        this.sort = sort;
    }
    
    public String getCreator() {
        return creator;
    }
    
    public void setCreator(String creator) {
        this.creator = creator;
    }
    
    public Date getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
    
    public String getUpdater() {
        return updater;
    }
    
    public void setUpdater(String updater) {
        this.updater = updater;
    }
    
    public Date getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public void setAppId(String appId) {
        this.appId = appId;
    }
    
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }
    
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }
    
    public Boolean getStreamEnabled() {
        return streamEnabled;
    }
    
    public void setStreamEnabled(Boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
    }
    
    public Boolean getFileUploadEnabled() {
        return fileUploadEnabled;
    }
    
    public void setFileUploadEnabled(Boolean fileUploadEnabled) {
        this.fileUploadEnabled = fileUploadEnabled;
    }
    
    public Boolean getInputEnabled() {
        return inputEnabled;
    }
    
    public void setInputEnabled(Boolean inputEnabled) {
        this.inputEnabled = inputEnabled;
    }
    
    public String getThemeColor() {
        return themeColor;
    }
    
    public void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }
}