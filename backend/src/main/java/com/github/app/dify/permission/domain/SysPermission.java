package com.github.app.dify.permission.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "SYS_PERMISSION")
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "权限编码")
    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Schema(description = "权限名称")
    @Column(name = "permission_name", nullable = false, length = 100)
    private String permissionName;

    @Schema(description = "端类型：admin/user")
    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType;

    @Schema(description = "前端路由路径")
    @Column(name = "route_path", length = 200)
    private String routePath;

    @Schema(description = "图标名称")
    @Column(name = "icon", length = 80)
    private String icon;

    @Schema(description = "排序")
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Schema(description = "状态：1-启用，0-停用")
    @Column(name = "status")
    private Integer status;

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
