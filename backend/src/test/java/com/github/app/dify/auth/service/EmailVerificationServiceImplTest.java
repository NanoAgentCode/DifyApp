package com.github.app.dify.auth.service;

import com.github.app.dify.auth.config.VerificationCodeProperties;
import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.service.email.VerificationEmailTemplateRenderer;
import com.github.app.dify.auth.service.impl.EmailVerificationServiceImpl;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JavaMailSender mailSender;

    private EmailVerificationServiceImpl service;
    private VerificationCodeProperties verificationProperties;

    @BeforeEach
    void setUp() {
        verificationProperties = new VerificationCodeProperties();
        verificationProperties.setFrom("noreply@example.com");
        verificationProperties.setSenderName("DifyApp Team");
        MailProperties mailProperties = new MailProperties();
        mailProperties.setHost("smtp.example.com");
        VerificationEmailTemplateRenderer renderer = new VerificationEmailTemplateRenderer(
                createTemplateEngine(), verificationProperties);
        service = new EmailVerificationServiceImpl(
                redisTemplate, mailSender, renderer, verificationProperties, mailProperties);
    }

    @Test
    void sendCodeStoresSixDigitsAndSendsMail() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendCode("User@Example.com", VerificationCodePurpose.REGISTER);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), codeCaptor.capture(), any(Duration.class));
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));

        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        mailCaptor.getValue().saveChanges();
        assertEquals("DifyApp 注册账号验证码", mailCaptor.getValue().getSubject());
        assertEquals("user@example.com", mailCaptor.getValue().getAllRecipients()[0].toString());
        InternetAddress fromAddress = (InternetAddress) mailCaptor.getValue().getFrom()[0];
        assertEquals("noreply@example.com", fromAddress.getAddress());
        assertEquals("DifyApp Team", fromAddress.getPersonal());
        assertTrue(containsMimeType(mailCaptor.getValue(), "multipart/alternative"));
        assertTrue(containsMimeType(mailCaptor.getValue(), "text/plain"));
        assertTrue(containsMimeType(mailCaptor.getValue(), "text/html"));
    }

    @Test
    void sendCodeEnforcesResendCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendCode("user@example.com", VerificationCodePurpose.REGISTER));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, error.getCode());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void verifyConsumesValidCode() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(), any(Object[].class))).thenReturn(1L);

        service.verifyAndConsume("user@example.com", VerificationCodePurpose.RESET_PASSWORD, "123456");

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(), any(Object[].class));
    }

    @Test
    void verifyRejectsExpiredCode() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(), any(Object[].class))).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(
                        "user@example.com", VerificationCodePurpose.RESET_PASSWORD, "123456"));

        assertEquals(ErrorCode.CAPTCHA_EXPIRED, error.getCode());
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

    private static boolean containsMimeType(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return true;
        }
        if (!part.isMimeType("multipart/*")) {
            return false;
        }
        MimeMultipart multipart = (MimeMultipart) part.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            if (containsMimeType(multipart.getBodyPart(i), mimeType)) {
                return true;
            }
        }
        return false;
    }
}
