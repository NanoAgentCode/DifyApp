package com.github.app.dify.auth.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * 管理员重置用户密码请求
 */
@Schema(description = "管理员重置用户密码请求")
public class ResetPasswordRequest {
    
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 255, message = "新密码长度必须在6-255之间")
    @Schema(description = "新密码")
    private String newPassword;
    
    // Getters and Setters
    public String getNewPassword() {
        return newPassword;
    }
    
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

