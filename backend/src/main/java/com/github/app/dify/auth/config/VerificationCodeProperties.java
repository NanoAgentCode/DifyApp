package com.github.app.dify.auth.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 邮箱验证码策略与邮件品牌配置。
 */
@Validated
@Configuration
@ConfigurationProperties(prefix = "auth.verification-code")
public class VerificationCodeProperties {

    @NotBlank
    @Email
    private String from = "noreply@difyapp.local";

    @NotBlank
    private String senderName = "DifyApp";

    @NotBlank
    private String brandName = "DifyApp";

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "邮件品牌色必须是六位十六进制颜色")
    private String brandColor = "#409EFF";

    @Min(1)
    private long ttlMinutes = 5;

    @Min(1)
    private long cooldownSeconds = 60;

    @Min(1)
    private long hourlyLimit = 10;

    @Min(1)
    private long maxAttempts = 5;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getBrandColor() {
        return brandColor;
    }

    public void setBrandColor(String brandColor) {
        this.brandColor = brandColor;
    }

    public long getTtlMinutes() {
        return ttlMinutes;
    }

    public void setTtlMinutes(long ttlMinutes) {
        this.ttlMinutes = ttlMinutes;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public long getHourlyLimit() {
        return hourlyLimit;
    }

    public void setHourlyLimit(long hourlyLimit) {
        this.hourlyLimit = hourlyLimit;
    }

    public long getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(long maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
