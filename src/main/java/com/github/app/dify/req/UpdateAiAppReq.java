package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Size;

/**
 * 更新AI应用请求
 */
@ApiModel("更新AI应用请求")
public class UpdateAiAppReq {
    
    @Size(max = 100, message = "应用名称长度不能超过100")
    @ApiModelProperty("应用名称")
    private String name;
    
    @Size(max = 500, message = "应用描述长度不能超过500")
    @ApiModelProperty("应用描述")
    private String description;
    
    @ApiModelProperty("应用状态")
    private Integer status;
    
    @Size(max = 500, message = "API Base URL长度不能超过500")
    @ApiModelProperty("Dify API Base URL")
    private String apiBaseUrl;
    
    @ApiModelProperty("是否支持流式响应")
    private Boolean streamEnabled;
    
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
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
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
}

