package com.github.app.dify.auth.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
public class User extends BaseSoftDeleteEntity {

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
    
}

