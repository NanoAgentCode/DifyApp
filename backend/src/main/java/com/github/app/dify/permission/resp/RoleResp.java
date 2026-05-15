package com.github.app.dify.permission.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Schema(description = "角色响应")
public class RoleResp {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private Integer sortOrder;
    private Boolean systemRole;
    private Date createTime;
    private Date updateTime;
    private List<String> permissionCodes;
}
