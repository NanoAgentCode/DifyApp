package com.github.app.dify.permission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "SYS_USER_ROLE", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sys_user_role", columnNames = {"user_id", "role_id"})
})
public class SysUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "create_time")
    private Date createTime;
}
