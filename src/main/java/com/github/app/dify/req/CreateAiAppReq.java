package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 创建AI应用请求
 */
@ApiModel("创建AI应用请求")
public class CreateAiAppReq {
    
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称长度不能超过100")
    @ApiModelProperty(value = "应用名称", required = true)
    private String name;
    
    @Size(max = 500, message = "应用描述长度不能超过500")
    @ApiModelProperty("应用描述")
    private String description;
    
    @NotNull(message = "应用类型不能为空")
    @ApiModelProperty(value = "应用类型：1-chatFlow，2-workflow", required = true)
    private Integer type;
    
    @NotBlank(message = "Dify API Key不能为空")
    @Size(max = 255, message = "API Key长度不能超过255")
    @ApiModelProperty(value = "Dify API Key", required = true)
    private String appId;
    
    @Size(max = 500, message = "API Base URL长度不能超过500")
    @ApiModelProperty("Dify API Base URL")
    private String apiBaseUrl;
    
    @ApiModelProperty("是否支持流式响应")
    private Boolean streamEnabled;
    
    @ApiModelProperty("是否需要上传文件")
    private Boolean fileUploadEnabled;
    
    @ApiModelProperty("是否显示文本输入框")
    private Boolean inputEnabled;
    
    @Size(max = 255, message = "应用图标长度不能超过255")
    @ApiModelProperty("应用图标")
    private String icon;
    
    @ApiModelProperty("排序")
    private Integer sort;
    
    @Size(max = 64, message = "主题色长度不能超过64")
    @ApiModelProperty("主题色")
    private String themeColor;
    
    @ApiModelProperty("应用配置JSON")
    private String inputs;
    
    @NotNull(message = "租户编号不能为空")
    @ApiModelProperty(value = "租户编号", required = true)
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

