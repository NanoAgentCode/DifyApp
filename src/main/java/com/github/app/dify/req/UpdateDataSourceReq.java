package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.Size;

/**
 * 更新数据源请求
 */
@ApiModel("更新数据源请求")
public class UpdateDataSourceReq {
    
    @Size(max = 100, message = "数据源名称长度不能超过100")
    @ApiModelProperty("数据源名称")
    private String name;
    
    @Size(max = 500, message = "数据源描述长度不能超过500")
    @ApiModelProperty("数据源描述")
    private String description;
    
    @Size(max = 20, message = "数据库类型长度不能超过20")
    @ApiModelProperty("数据库类型：postgresql, mysql, oracle, mongodb")
    private String type;
    
    @Size(max = 255, message = "主机地址长度不能超过255")
    @ApiModelProperty("主机地址")
    private String host;
    
    @ApiModelProperty("端口号")
    private Integer port;
    
    @Size(max = 100, message = "数据库名称长度不能超过100")
    @ApiModelProperty("数据库名称")
    private String database;
    
    @Size(max = 100, message = "用户名长度不能超过100")
    @ApiModelProperty("用户名")
    private String username;
    
    @Size(max = 500, message = "密码长度不能超过500")
    @ApiModelProperty("密码（如果为空则不更新）")
    private String password;
    
    @ApiModelProperty("数据源状态：1-启用，0-禁用")
    private Integer status;
    
    @ApiModelProperty("是否公开：true-公开，false-私有")
    private Boolean isPublic;
    
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
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public Boolean getIsPublic() {
        return isPublic;
    }
    
    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}

