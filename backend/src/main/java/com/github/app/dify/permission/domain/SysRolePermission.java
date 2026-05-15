package com.github.app.dify.permission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "SYS_ROLE_PERMISSION", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_role_permission", columnNames = {"role_id", "permission_id"})
})
public class SysRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "create_time")
    private Date createTime;
}
