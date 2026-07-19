package com.github.app.dify.auth.service;

import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.service.impl.EmailVerificationServiceImpl;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
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

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl(redisTemplate, mailSender);
        ReflectionTestUtils.setField(service, "mailHost", "smtp.example.com");
        ReflectionTestUtils.setField(service, "from", "noreply@example.com");
        ReflectionTestUtils.setField(service, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(service, "hourlyLimit", 10L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5L);
    }

    @Test
    void sendCodeStoresSixDigitsAndSendsMail() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        MimeMessage message = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendCode("User@Example.com", VerificationCodePurpose.REGISTER);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), codeCaptor.capture(), any(Duration.class));
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));

        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        assertEquals("DifyApp 注册账号验证码", mailCaptor.getValue().getSubject());
        assertEquals("user@example.com", mailCaptor.getValue().getAllRecipients()[0].toString());
        assertTrue(mailCaptor.getValue().getContentType().contains("multipart/alternative"));
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
}
