package com.github.app.dify.auth.service.impl;

import com.github.app.dify.auth.req.VerificationCodePurpose;
import com.github.app.dify.auth.service.EmailVerificationService;
import com.github.app.dify.auth.util.VerificationEmailTemplate;
import com.github.app.dify.auth.util.VerificationEmailTemplate.EmailContent;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "difyapp:auth:email-code:";

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
                return 0
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end
            local attempts = redis.call('INCR', KEYS[2])
            if attempts == 1 then
                local ttl = redis.call('TTL', KEYS[1])
                if ttl > 0 then
                    redis.call('EXPIRE', KEYS[2], ttl)
                else
                    redis.call('EXPIRE', KEYS[2], ARGV[3])
                end
            end
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return -1
            end
            return -2
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${auth.verification-code.from:noreply@difyapp.local}")
    private String from;

    @Value("${auth.verification-code.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${auth.verification-code.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${auth.verification-code.hourly-limit:10}")
    private long hourlyLimit;

    @Value("${auth.verification-code.max-attempts:5}")
    private long maxAttempts;

    public EmailVerificationServiceImpl(StringRedisTemplate redisTemplate, JavaMailSender mailSender) {
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    @Override
    public void sendCode(String email, VerificationCodePurpose purpose) {
        ensureMailConfigured();

        String normalizedEmail = normalizeEmail(email);
        String keySuffix = keySuffix(normalizedEmail, purpose);
        String codeKey = KEY_PREFIX + "value:" + keySuffix;
        String attemptsKey = KEY_PREFIX + "attempts:" + keySuffix;
        String cooldownKey = KEY_PREFIX + "cooldown:" + keySuffix;
        String hourlyKey = KEY_PREFIX + "hourly:" + keySuffix;
        String code = String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new BusinessException("验证码发送过于频繁，请稍后再试", ErrorCode.TOO_MANY_REQUESTS);
            }

            Long sentCount = redisTemplate.opsForValue().increment(hourlyKey);
            if (sentCount != null && sentCount == 1L) {
                redisTemplate.expire(hourlyKey, Duration.ofHours(1));
            }
            if (sentCount != null && sentCount > hourlyLimit) {
                redisTemplate.delete(cooldownKey);
                throw new BusinessException("验证码发送次数已达上限，请一小时后再试", ErrorCode.TOO_MANY_REQUESTS);
            }

            redisTemplate.delete(attemptsKey);
            redisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(ttlMinutes));
            sendMail(normalizedEmail, purpose, code);
            logger.info("邮箱验证码发送成功 - purpose: {}, email: {}", purpose, maskEmail(normalizedEmail));
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Redis不可用，无法保存邮箱验证码", e);
            throw new BusinessException("验证码服务暂不可用，请稍后再试", ErrorCode.SERVICE_UNAVAILABLE);
        } catch (MailException e) {
            deleteQuietly(codeKey, attemptsKey, cooldownKey);
            logger.error("邮箱验证码发送失败 - purpose: {}, email: {}", purpose, maskEmail(normalizedEmail), e);
            throw new BusinessException("邮件发送失败，请检查邮箱或稍后再试", ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException e) {
            deleteQuietly(codeKey, attemptsKey, cooldownKey);
            logger.error("邮箱验证码发送异常 - purpose: {}, email: {}", purpose, maskEmail(normalizedEmail), e);
            throw new BusinessException("邮件发送失败，请稍后再试", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    public void verifyAndConsume(String email, VerificationCodePurpose purpose, String code) {
        String normalizedEmail = normalizeEmail(email);
        String keySuffix = keySuffix(normalizedEmail, purpose);
        String codeKey = KEY_PREFIX + "value:" + keySuffix;
        String attemptsKey = KEY_PREFIX + "attempts:" + keySuffix;

        try {
            Long result = redisTemplate.execute(
                    VERIFY_SCRIPT,
                    List.of(codeKey, attemptsKey),
                    code,
                    String.valueOf(maxAttempts),
                    String.valueOf(Duration.ofMinutes(ttlMinutes).toSeconds())
            );
            if (result == null || result == 0L) {
                throw new BusinessException("邮箱验证码已过期，请重新获取", ErrorCode.CAPTCHA_EXPIRED);
            }
            if (result == -1L) {
                throw new BusinessException("邮箱验证码错误次数过多，请重新获取", ErrorCode.CAPTCHA_ERROR);
            }
            if (result != 1L) {
                throw new BusinessException("邮箱验证码错误", ErrorCode.CAPTCHA_ERROR);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            logger.error("Redis不可用，无法校验邮箱验证码", e);
            throw new BusinessException("验证码服务暂不可用，请稍后再试", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void sendMail(String email, VerificationCodePurpose purpose, String code) {
        EmailContent content = VerificationEmailTemplate.render(purpose, code, ttlMinutes);
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(content.subject());
            helper.setText(content.plainText(), content.htmlText());
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException("无法创建邮箱验证码邮件", e);
        }
    }

    private void ensureMailConfigured() {
        if (mailHost == null || mailHost.isBlank()) {
            throw new BusinessException("邮件服务尚未配置，请联系管理员", ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private String keySuffix(String email, VerificationCodePurpose purpose) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
            return purpose.name().toLowerCase(Locale.ROOT) + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private void deleteQuietly(String... keys) {
        try {
            redisTemplate.delete(List.of(keys));
        } catch (DataAccessException cleanupError) {
            logger.warn("清理未发送的邮箱验证码失败", cleanupError);
        }
    }
}
