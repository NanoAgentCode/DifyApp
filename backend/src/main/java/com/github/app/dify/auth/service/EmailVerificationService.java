package com.github.app.dify.auth.service;

import com.github.app.dify.auth.req.VerificationCodePurpose;

public interface EmailVerificationService {

    void sendCode(String email, VerificationCodePurpose purpose);

    void verifyAndConsume(String email, VerificationCodePurpose purpose, String code);
}
