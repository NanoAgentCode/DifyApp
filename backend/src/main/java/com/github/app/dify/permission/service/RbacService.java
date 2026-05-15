package com.github.app.dify.permission.service;

import com.github.app.dify.permission.req.RoleSaveReq;
import com.github.app.dify.permission.resp.PermissionResp;
import com.github.app.dify.permission.resp.RoleResp;

import java.util.List;
import java.util.Set;

public interface RbacService {
    List<RoleResp> listRoles();

    RoleResp createRole(RoleSaveReq req);

    RoleResp updateRole(Long roleId, RoleSaveReq req);

    void deleteRole(Long roleId);

    List<PermissionResp> listPermissions();

    List<String> getUserPermissionCodes(Long userId);

    List<RoleResp> getUserRoles(Long userId);

    void updateUserRoles(Long userId, List<Long> roleIds);

    List<Long> getRolePermissionIds(Long roleId);

    void updateRolePermissions(Long roleId, List<Long> permissionIds);

    boolean hasPermission(Long userId, String permissionCode);

    boolean isSuperAdmin(Long userId);

    Set<Long> getSuperAdminRoleIds();
}
