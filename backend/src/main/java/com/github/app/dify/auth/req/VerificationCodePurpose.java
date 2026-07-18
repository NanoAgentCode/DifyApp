package com.github.app.dify.auth.req;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "邮箱验证码用途")
public enum VerificationCodePurpose {
    REGISTER,
    RESET_PASSWORD
}
