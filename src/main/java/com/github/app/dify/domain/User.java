package com.github.app.dify.domain;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.Length;

/**
 * 用户表
 * @TableName SYS_USER
 */
@Entity
@Table(name = "SYS_USER")
public class User implements Serializable {

    /**
     * 用户编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("用户编号")
    private Long id;
    
    /**
     * 用户名
     */
    @NotBlank(message="[用户名]不能为空")
    @Size(max= 64,message="编码长度不能超过64")
    @ApiModelProperty("用户名")
    @Length(max= 64,message="编码长度不能超过64")
    @Column(name = "username", unique = true)
    private String username;
    
    /**
     * 密码（加密后）
     */
    @NotBlank(message="[密码]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @ApiModelProperty("密码（加密后）")
    @Length(max= 255,message="编码长度不能超过255")
    private String password;
    
    /**
     * 角色：1-管理员，2-普通用户
     */
    @ApiModelProperty("角色：1-管理员，2-普通用户")
    @Column(name = "role")
    private Integer role;
    
    /**
     * 状态：0-待审核，1-已激活，2-已禁用
     */
    @ApiModelProperty("状态：0-待审核，1-已激活，2-已禁用")
    @Column(name = "status")
    private Integer status;
    
    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @ApiModelProperty("更新时间")
    @Column(name = "update_time")
    private Date updateTime;
    
    /**
     * 是否删除
     */
    @ApiModelProperty("是否删除")
    @Column(name = "deleted")
    private Integer deleted;

    /**
     * 用户编号
     */
    public void setId(Long id){
        this.id = id;
    }

    /**
     * 用户名
     */
    public void setUsername(String username){
        this.username = username;
    }

    /**
     * 密码（加密后）
     */
    public void setPassword(String password){
        this.password = password;
    }

    /**
     * 角色
     */
    public void setRole(Integer role){
        this.role = role;
    }

    /**
     * 状态
     */
    public void setStatus(Integer status){
        this.status = status;
    }

    /**
     * 创建时间
     */
    public void setCreateTime(Date createTime){
        this.createTime = createTime;
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
     * 用户编号
     */
    public Long getId(){
        return this.id;
    }

    /**
     * 用户名
     */
    public String getUsername(){
        return this.username;
    }

    /**
     * 密码（加密后）
     */
    public String getPassword(){
        return this.password;
    }

    /**
     * 角色
     */
    public Integer getRole(){
        return this.role;
    }

    /**
     * 状态
     */
    public Integer getStatus(){
        return this.status;
    }

    /**
     * 创建时间
     */
    public Date getCreateTime(){
        return this.createTime;
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
}

