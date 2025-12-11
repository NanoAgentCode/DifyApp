package com.github.app.dify.appaichat.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
/**
 * 创建AI应用请求
 */
@Schema(description = "创建AI应用请求")
public class CreateAiAppReq {
    
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称长度不能超过100")
    @Schema(description = "应用名称")
    private String name;
    
    @Size(max = 500, message = "应用描述长度不能超过500")
    @Schema(description = "应用描述")
    private String description;
    
    @NotNull(message = "应用类型不能为空")
    @Schema(description = "应用类型：1-chatFlow，2-workflow")
    private Integer type;
    
    @NotBlank(message = "Dify API Key不能为空")
    @Size(max = 255, message = "API Key长度不能超过255")
    @Schema(description = "Dify API Key")
    private String appId;
    
    @Size(max = 500, message = "API Base URL长度不能超过500")
    @Schema(description = "Dify API Base URL")
    private String apiBaseUrl;
    
    @Schema(description = "是否支持流式响应")
    private Boolean streamEnabled;
    
    @Schema(description = "是否需要上传文件")
    private Boolean fileUploadEnabled;
    
    @Schema(description = "是否显示文本输入框")
    private Boolean inputEnabled;
    
    @Size(max = 255, message = "应用图标长度不能超过255")
    @Schema(description = "应用图标")
    private String icon;
    
    @Schema(description = "排序")
    private Integer sort;
    
    @Size(max = 64, message = "主题色长度不能超过64")
    @Schema(description = "主题色")
    private String themeColor;
    
    @Schema(description = "应用配置JSON")
    private String inputs;
    
    @NotNull(message = "租户编号不能为空")
    @Schema(description = "租户编号")
    private Integer tenantId;
    
    // Getters and Setters
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
    
    public String getThemeColor() {
        return themeColor;
    }
    
    public void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }
    
    public String getInputs() {
        return inputs;
    }
    
    public void setInputs(String inputs) {
        this.inputs = inputs;
    }
    
    public Integer getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }
}