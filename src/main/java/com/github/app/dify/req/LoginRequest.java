package com.github.app.dify.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 登录请求
 */
@Schema(description = "登录请求")
public class LoginRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    @Schema(description = "用户名")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(max = 255, message = "密码长度不能超过255")
    @Schema(description = "密码")
    private String password;
    
    // Getters and Setters
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}