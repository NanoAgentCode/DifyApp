package com.github.app.dify.permission.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "SYS_ROLE")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色编码")
    @Column(name = "role_code", nullable = false, unique = true, length = 64)
    private String roleCode;

    @Schema(description = "角色名称")
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Schema(description = "角色描述")
    @Column(name = "description", length = 500)
    private String description;

    @Schema(description = "状态：1-启用，0-停用")
    @Column(name = "status")
    private Integer status;

    @Schema(description = "排序")
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Schema(description = "是否系统内置角色")
    @Column(name = "system_role")
    private Boolean systemRole;

    @Schema(description = "创建时间")
    @Column(name = "create_time")
    private Date createTime;

    @Schema(description = "更新时间")
    @Column(name = "update_time")
    private Date updateTime;

    @Schema(description = "是否删除：0-未删除，1-已删除")
    @Column(name = "deleted")
    private Integer deleted;
}
