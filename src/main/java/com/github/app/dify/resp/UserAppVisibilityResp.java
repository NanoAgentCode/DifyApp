package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户应用可见性响应
 */
@ApiModel("用户应用可见性响应")
public class UserAppVisibilityResp {
    
    @ApiModelProperty("应用ID")
    private Long appId;
    
    @ApiModelProperty("应用名称")
    private String appName;
    
    @ApiModelProperty("应用描述")
    private String appDescription;
    
    @ApiModelProperty("应用类型：1-chatFlow，2-workflow")
    private Integer appType;
    
    @ApiModelProperty("是否可见")
    private Boolean visible;
    
    // Getters and Setters
    public Long getAppId() {
        return appId;
    }
    
    public void setAppId(Long appId) {
        this.appId = appId;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getAppDescription() {
        return appDescription;
    }
    
    public void setAppDescription(String appDescription) {
        this.appDescription = appDescription;
    }
    
    public Integer getAppType() {
        return appType;
    }
    
    public void setAppType(Integer appType) {
        this.appType = appType;
    }
    
    public Boolean getVisible() {
        return visible;
    }
    
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
}

