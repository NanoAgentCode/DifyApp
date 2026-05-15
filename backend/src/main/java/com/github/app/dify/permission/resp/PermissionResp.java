package com.github.app.dify.permission.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "模块权限响应")
public class PermissionResp {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String clientType;
    private String routePath;
    private String icon;
    private Integer sortOrder;
    private Integer status;
}
