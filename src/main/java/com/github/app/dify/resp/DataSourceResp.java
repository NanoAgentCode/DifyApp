package com.github.app.dify.resp;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 数据源响应
 */
@ApiModel("数据源响应")
public class DataSourceResp {
    
    @ApiModelProperty("数据源编号")
    private Long id;
    
    @ApiModelProperty("数据源名称")
    private String name;
    
    @ApiModelProperty("数据源描述")
    private String description;
    
    @ApiModelProperty("数据库类型：postgresql, mysql, oracle, mongodb")
    private String type;
    
    @ApiModelProperty("主机地址")
    private String host;
    
    @ApiModelProperty("端口号")
    private Integer port;
    
    @ApiModelProperty("数据库名称")
    private String database;
    
    @ApiModelProperty("用户名")
    private String username;
    
    @ApiModelProperty("数据源状态：1-启用，0-禁用")
    private Integer status;
    
    @ApiModelProperty("创建者")
    private String creator;
    
    @ApiModelProperty("创建者ID")
    private Long creatorId;
    
    @ApiModelProperty("是否公开：true-公开，false-私有")
    private Boolean isPublic;
    
    @ApiModelProperty("创建时间")
    private Date createTime;
    
    @ApiModelProperty("更新者")
    private String updater;
    
    @ApiModelProperty("更新时间")
    private Date updateTime;
    
    @ApiModelProperty("租户编号")
    private Integer tenantId;
    
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
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
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
    
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
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
}

