package com.github.app.dify.auth.resp;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 注册响应
 */
@Schema(description = "注册响应")
public class RegisterResponse {
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "邮箱")
    private String email;
    
    @Schema(description = "状态：0-待审核，1-已激活，2-已禁用")
    private Integer status;
    
    @Schema(description = "提示信息")
    private String message;
    
    // Getters and Setters
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
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}

