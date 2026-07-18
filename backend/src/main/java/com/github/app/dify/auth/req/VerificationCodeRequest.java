package com.github.app.dify.auth.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "发送邮箱验证码请求")
public class VerificationCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过254")
    @Schema(description = "邮箱")
    private String email;

    @NotNull(message = "验证码用途不能为空")
    @Schema(description = "验证码用途：REGISTER 或 RESET_PASSWORD")
    private VerificationCodePurpose purpose;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public VerificationCodePurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(VerificationCodePurpose purpose) {
        this.purpose = purpose;
    }
}
