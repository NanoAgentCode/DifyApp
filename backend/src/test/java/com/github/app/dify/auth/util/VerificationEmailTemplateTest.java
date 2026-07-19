package com.github.app.dify.auth.util;

import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.util.VerificationEmailTemplate.EmailContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationEmailTemplateTest {

    @Test
    void rendersRegistrationEmailWithCodeExpiryAndReviewNotice() {
        EmailContent content = VerificationEmailTemplate.render(
                VerificationCodePurpose.REGISTER, "123456", 5L);

        assertEquals("DifyApp 注册账号验证码", content.subject());
        assertTrue(content.plainText().contains("123456"));
        assertTrue(content.htmlText().contains("完成账号注册"));
        assertTrue(content.htmlText().contains("123456"));
        assertTrue(content.htmlText().contains("5 分钟"));
        assertTrue(content.htmlText().contains("管理员审核"));
        assertFalse(content.htmlText().contains("{{"));
    }

    @Test
    void rendersPasswordResetSpecificCopy() {
        EmailContent content = VerificationEmailTemplate.render(
                VerificationCodePurpose.RESET_PASSWORD, "654321", 10L);

        assertEquals("DifyApp 重置密码验证码", content.subject());
        assertTrue(content.htmlText().contains("验证身份并重置密码"));
        assertTrue(content.htmlText().contains("654321"));
        assertTrue(content.htmlText().contains("10 分钟"));
        assertFalse(content.htmlText().contains("账号将进入管理员审核"));
    }
}
