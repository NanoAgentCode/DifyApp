package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户数据源可见性响应
 */
@ApiModel("用户数据源可见性响应")
public class UserDataSourceVisibilityResp {
    
    @ApiModelProperty("数据源ID")
    private Long dataSourceId;
    
    @ApiModelProperty("数据源名称")
    private String dataSourceName;
    
    @ApiModelProperty("数据源描述")
    private String dataSourceDescription;
    
    @ApiModelProperty("数据源类型")
    private String dataSourceType;
    
    @ApiModelProperty("数据源状态")
    private Integer dataSourceStatus;
    
    @ApiModelProperty("是否可见：true-可见，false-不可见")
    private Boolean visible;
    
    // Getters and Setters
    public Long getDataSourceId() {
        return dataSourceId;
    }
    
    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }
    
    public String getDataSourceName() {
        return dataSourceName;
    }
    
    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }
    
    public String getDataSourceDescription() {
        return dataSourceDescription;
    }
    
    public void setDataSourceDescription(String dataSourceDescription) {
        this.dataSourceDescription = dataSourceDescription;
    }
    
    public String getDataSourceType() {
        return dataSourceType;
    }
    
    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }
    
    public Integer getDataSourceStatus() {
        return dataSourceStatus;
    }
    
    public void setDataSourceStatus(Integer dataSourceStatus) {
        this.dataSourceStatus = dataSourceStatus;
    }
    
    public Boolean getVisible() {
        return visible;
    }
    
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }
}

