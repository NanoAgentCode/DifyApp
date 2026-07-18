package com.github.app.dify.auth.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import com.github.app.dify.permission.resp.RoleResp;
import java.util.List;
/**
 * 登录响应
 */
@Schema(description = "登录响应")
public class LoginResponse {
    
    @Schema(description = "访问令牌")
    private String token;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "角色：1-管理员，2-普通用户")
    private Integer role;
    
    @Schema(description = "状态：0-待审核，1-已激活，2-已禁用")
    private Integer status;

    @Schema(description = "RBAC角色列表")
    private List<RoleResp> roles;

    @Schema(description = "RBAC模块权限编码列表")
    private List<String> permissions;
    
    // Getters and Setters
    public String getToken() {
        return token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    public Integer getRole() {
        return role;
    }
    
    public void setRole(Integer role) {
        this.role = role;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<RoleResp> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleResp> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}

