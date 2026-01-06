package com.github.app.dify.auth.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;
/**
 * 用户表
 * @TableName SYS_USER
 */
@Setter
@Getter
@Entity
@Table(name = "SYS_USER")
public class User implements Serializable {

    /**
     * 用户编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "用户编号")
    private Long id;
    
    /**
     * 用户名
     */
    @NotBlank(message="[用户名]不能为空")
    @Size(max= 64,message="编码长度不能超过64")
    @Schema(description = "用户名")
    @Length(max= 64,message="编码长度不能超过64")
    @Column(name = "username", unique = true)
    private String username;
    
    /**
     * 密码（加密后）
     */
    @NotBlank(message="[密码]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @Schema(description = "密码（加密后）")
    @Length(max= 255,message="编码长度不能超过255")
    private String password;
    
    /**
     * 角色：1-管理员，2-普通用户
     */
    @Schema(description = "角色：1-管理员，2-普通用户")
    @Column(name = "role")
    private Integer role;
    
    /**
     * 状态：0-待审核，1-已激活，2-已禁用
     */
    @Schema(description = "状态：0-待审核，1-已激活，2-已禁用")
    @Column(name = "status")
    private Integer status;
    
    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;
    
    /**
     * 是否删除
     */
    @Schema(description = "是否删除")
    @Column(name = "deleted")
    private Integer deleted;

}

