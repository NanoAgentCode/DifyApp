package com.github.app.dify.appaichat.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
/**
* AI 应用与用户关联表
* @TableName AI_APP_USER
*/
@Entity
@Table(name = "AI_APP_USER")
public class AiAppUser implements Serializable {

    /**
    * 关联编号
    */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NotNull(message="[关联编号]不能为空")
    @Schema(description = "关联编号")
    private Long id;
    /**
    * 应用编号
    */
    @NotNull(message="[应用编号]不能为空")
    @Schema(description = "应用编号")
    private Long appId;
    /**
    * 用户编号
    */
    @NotBlank(message="[用户编号]不能为空")
    @Size(max= 50,message="编码长度不能超过50")
    @Schema(description = "用户编号")
    @Length(max= 50,message="编码长度不能超过50")
    private String userId;
    /**
    * 用户名称
    */
    @Size(max= 100,message="编码长度不能超过100")
    @Schema(description = "用户名称")
    @Length(max= 100,message="编码长度不能超过100")
    private String userName;
    /**
    * 角色类型：1-普通用户，2-管理员，3-超级管理员
    */
    @Schema(description = "角色类型：1-普通用户，2-管理员，3-超级管理员")
    private Integer roleType;
    /**
    * 状态：0-禁用，1-启用
    */
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;
    /**
    * 权限配置JSON
    */
    @Size(max= 1000,message="编码长度不能超过1000")
    @Schema(description = "权限配置JSON")
    @Length(max= 1000,message="编码长度不能超过1000")
    private String permissions;
    /**
    * 创建者
    */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "创建者")
    @Length(max= 64,message="编码长度不能超过64")
    private String creator;
    /**
    * 创建时间
    */
    @Schema(description = "创建时间")
    private Date createTime;
    /**
    * 更新者
    */
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "更新者")
    @Length(max= 64,message="编码长度不能超过64")
    private String updater;
    /**
    * 更新时间
    */
    @Schema(description = "更新时间")
    private Date updateTime;
    /**
    * 是否删除
    */
    @Schema(description = "是否删除")
    private Integer deleted;
    /**
    * 租户编号
    */
    @NotNull(message="[租户编号]不能为空")
    @Schema(description = "租户编号")
    private Integer tenantId;

    /**
    * 关联编号
    */
    public void setId(Long id){
    this.id = id;
    }

    /**
    * 应用编号
    */
    public void setAppId(Long appId){
    this.appId = appId;
    }

    /**
    * 用户编号
    */
    public void setUserId(String userId){
    this.userId = userId;
    }

    /**
    * 用户名称
    */
    public void setUserName(String userName){
    this.userName = userName;
    }

    /**
    * 角色类型：1-普通用户，2-管理员，3-超级管理员
    */
    public void setRoleType(Integer roleType){
    this.roleType = roleType;
    }

    /**
    * 状态：0-禁用，1-启用
    */
    public void setStatus(Integer status){
    this.status = status;
    }

    /**
    * 权限配置JSON
    */
    public void setPermissions(String permissions){
    this.permissions = permissions;
    }

    /**
    * 创建者
    */
    public void setCreator(String creator){
    this.creator = creator;
    }

    /**
    * 创建时间
    */
    public void setCreateTime(Date createTime){
    this.createTime = createTime;
    }

    /**
    * 更新者
    */
    public void setUpdater(String updater){
    this.updater = updater;
    }

    /**
    * 更新时间
    */
    public void setUpdateTime(Date updateTime){
    this.updateTime = updateTime;
    }

    /**
    * 是否删除
    */
    public void setDeleted(Integer deleted){
    this.deleted = deleted;
    }

    /**
    * 租户编号
    */
    public void setTenantId(Integer tenantId){
    this.tenantId = tenantId;
    }


    /**
    * 关联编号
    */
    public Long getId(){
    return this.id;
    }

    /**
    * 应用编号
    */
    public Long getAppId(){
    return this.appId;
    }

    /**
    * 用户编号
    */
    public String getUserId(){
    return this.userId;
    }

    /**
    * 用户名称
    */
    public String getUserName(){
    return this.userName;
    }

    /**
    * 角色类型：1-普通用户，2-管理员，3-超级管理员
    */
    public Integer getRoleType(){
    return this.roleType;
    }

    /**
    * 状态：0-禁用，1-启用
    */
    public Integer getStatus(){
    return this.status;
    }

    /**
    * 权限配置JSON
    */
    public String getPermissions(){
    return this.permissions;
    }

    /**
    * 创建者
    */
    public String getCreator(){
    return this.creator;
    }

    /**
    * 创建时间
    */
    public Date getCreateTime(){
    return this.createTime;
    }

    /**
    * 更新者
    */
    public String getUpdater(){
    return this.updater;
    }

    /**
    * 更新时间
    */
    public Date getUpdateTime(){
    return this.updateTime;
    }

    /**
    * 是否删除
    */
    public Integer getDeleted(){
    return this.deleted;
    }

    /**
    * 租户编号
    */
    public Integer getTenantId(){
    return this.tenantId;
    }

}