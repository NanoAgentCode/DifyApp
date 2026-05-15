package com.github.app.dify.permission.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.permission.req.RolePermissionSaveReq;
import com.github.app.dify.permission.req.RoleSaveReq;
import com.github.app.dify.permission.req.UserRoleSaveReq;
import com.github.app.dify.permission.resp.PermissionResp;
import com.github.app.dify.permission.resp.RoleResp;
import com.github.app.dify.permission.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "RBAC权限管理")
@RestController
@RequestMapping("/api/rbac")
public class RbacController extends BaseController {

    @Autowired
    private RbacService rbacService;

    @Operation(summary = "获取当前用户模块权限码")
    @GetMapping("/my-permissions")
    public ResponseEntity<List<String>> myPermissions(HttpServletRequest request) {
        Long userId = getUserId(request);
        return ResponseEntity.ok(rbacService.getUserPermissionCodes(userId));
    }

    @Operation(summary = "获取模块权限列表")
    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionResp>> permissions(HttpServletRequest request) {
        requirePermission(request, "admin.roles");
        return ResponseEntity.ok(rbacService.listPermissions());
    }

    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    public ResponseEntity<List<RoleResp>> roles(HttpServletRequest request) {
        requirePermission(request, "admin.roles");
        return ResponseEntity.ok(rbacService.listRoles());
    }

    @Operation(summary = "新增角色")
    @PostMapping("/roles")
    public ResponseEntity<RoleResp> createRole(HttpServletRequest request, @Valid @RequestBody RoleSaveReq req) {
        requirePermission(request, "admin.roles");
        return ResponseEntity.ok(rbacService.createRole(req));
    }

    @Operation(summary = "编辑角色")
    @PutMapping("/roles/{roleId}")
    public ResponseEntity<RoleResp> updateRole(HttpServletRequest request, @PathVariable Long roleId,
                                               @Valid @RequestBody RoleSaveReq req) {
        requirePermission(request, "admin.roles");
        return ResponseEntity.ok(rbacService.updateRole(roleId, req));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<Void> deleteRole(HttpServletRequest request, @PathVariable Long roleId) {
        requirePermission(request, "admin.roles");
        rbacService.deleteRole(roleId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取角色模块权限ID")
    @GetMapping("/roles/{roleId}/permissions")
    public ResponseEntity<List<Long>> rolePermissions(HttpServletRequest request, @PathVariable Long roleId) {
        requirePermission(request, "admin.roles");
        return ResponseEntity.ok(rbacService.getRolePermissionIds(roleId));
    }

    @Operation(summary = "保存角色模块权限")
    @PutMapping("/roles/{roleId}/permissions")
    public ResponseEntity<Void> updateRolePermissions(HttpServletRequest request, @PathVariable Long roleId,
                                                      @RequestBody RolePermissionSaveReq req) {
        requirePermission(request, "admin.roles");
        rbacService.updateRolePermissions(roleId, req.getPermissionIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取用户角色")
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<List<RoleResp>> userRoles(HttpServletRequest request, @PathVariable Long userId) {
        requirePermission(request, "admin.users");
        return ResponseEntity.ok(rbacService.getUserRoles(userId));
    }

    @Operation(summary = "保存用户角色")
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<Void> updateUserRoles(HttpServletRequest request, @PathVariable Long userId,
                                                @RequestBody UserRoleSaveReq req) {
        requirePermission(request, "admin.users");
        rbacService.updateUserRoles(userId, req.getRoleIds());
        return ResponseEntity.ok().build();
    }

    private void requirePermission(HttpServletRequest request, String permissionCode) {
        Long userId = getUserId(request);
        if (!rbacService.hasPermission(userId, permissionCode)) {
            throw new ForbiddenException("无权访问该模块");
        }
    }
}
