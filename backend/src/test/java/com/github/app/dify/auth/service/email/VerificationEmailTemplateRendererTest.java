package com.github.app.dify.auth.service.email;

import com.github.app.dify.auth.config.VerificationCodeProperties;
import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.service.email.VerificationEmailTemplateRenderer.EmailContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerificationEmailTemplateRendererTest {

    private VerificationEmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        VerificationCodeProperties properties = new VerificationCodeProperties();
        properties.setBrandName("Acme AI");
        properties.setBrandColor("#123456");
        properties.setTtlMinutes(5L);
        renderer = new VerificationEmailTemplateRenderer(createTemplateEngine(), properties);
    }

    @Test
    void rendersRegistrationEmailFromResourceTemplate() {
        EmailContent content = renderer.render(VerificationCodePurpose.REGISTER, "123456");

        assertEquals("Acme AI 注册账号验证码", content.subject());
        assertTrue(content.plainText().contains("123456"));
        assertTrue(content.htmlText().contains("完成账号注册"));
        assertTrue(content.htmlText().contains("Acme AI"));
        assertTrue(content.htmlText().contains("#123456"));
        assertTrue(content.htmlText().contains("123456"));
        assertTrue(content.htmlText().contains("5 分钟"));
        assertTrue(content.htmlText().contains("管理员审核"));
        assertFalse(content.htmlText().contains("th:text"));
        assertFalse(content.htmlText().contains("${"));
    }

    @Test
    void rendersPasswordResetSpecificCopy() {
        EmailContent content = renderer.render(VerificationCodePurpose.RESET_PASSWORD, "654321");

        assertEquals("Acme AI 重置密码验证码", content.subject());
        assertTrue(content.htmlText().contains("验证身份并重置密码"));
        assertTrue(content.htmlText().contains("654321"));
        assertFalse(content.htmlText().contains("账号将进入管理员审核"));
    }

    private static SpringTemplateEngine createTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
