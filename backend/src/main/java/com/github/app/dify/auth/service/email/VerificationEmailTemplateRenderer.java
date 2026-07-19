package com.github.app.dify.auth.service.email;

import com.github.app.dify.auth.config.VerificationCodeProperties;
import com.github.app.dify.auth.req.VerificationCodePurpose;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/**
 * 将验证码场景数据交给资源模板渲染，不在 Java 源码中维护 HTML。
 */
@Component
public class VerificationEmailTemplateRenderer {

    private static final String TEMPLATE_NAME = "email/verification-code";

    private final ITemplateEngine templateEngine;
    private final VerificationCodeProperties properties;

    public VerificationEmailTemplateRenderer(
            ITemplateEngine templateEngine,
            VerificationCodeProperties properties) {
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public EmailContent render(VerificationCodePurpose purpose, String code) {
        EmailTemplateData data = buildTemplateData(purpose, code);
        Context context = new Context(Locale.SIMPLIFIED_CHINESE);
        context.setVariable("brandName", data.brandName());
        context.setVariable("brandColor", data.brandColor());
        context.setVariable("subject", data.subject());
        context.setVariable("preheader", data.preheader());
        context.setVariable("title", data.title());
        context.setVariable("intro", data.intro());
        context.setVariable("code", data.code());
        context.setVariable("ttlMinutes", data.ttlMinutes());
        context.setVariable("nextStep", data.nextStep());
        String htmlText = templateEngine.process(TEMPLATE_NAME, context);
        return new EmailContent(data.subject(), data.plainText(), htmlText);
    }

    private EmailTemplateData buildTemplateData(VerificationCodePurpose purpose, String code) {
        String brandName = properties.getBrandName();
        long ttlMinutes = properties.getTtlMinutes();
        return switch (purpose) {
            case REGISTER -> new EmailTemplateData(
                    brandName,
                    properties.getBrandColor(),
                    brandName + " 注册账号验证码",
                    "你的 " + brandName + " 注册账号验证码是 " + code + "，" + ttlMinutes + " 分钟内有效。",
                    "完成账号注册",
                    "感谢注册 " + brandName + "。请在注册页面输入以下验证码，完成邮箱验证。",
                    code,
                    ttlMinutes,
                    "完成注册后，账号将进入管理员审核；审核通过后即可登录使用 " + brandName + "。",
                    "你正在注册账号，验证码为：" + code + "。\n\n验证码将在 " + ttlMinutes
                            + " 分钟后失效，请勿向任何人泄露。若非本人操作，请忽略此邮件。"
                            + "\n\n完成注册后，账号需要等待管理员审核。"
            );
            case RESET_PASSWORD -> new EmailTemplateData(
                    brandName,
                    properties.getBrandColor(),
                    brandName + " 重置密码验证码",
                    "你的 " + brandName + " 重置密码验证码是 " + code + "，" + ttlMinutes + " 分钟内有效。",
                    "验证身份并重置密码",
                    "我们收到了你的密码重置请求。请在重置密码页面输入以下验证码。",
                    code,
                    ttlMinutes,
                    "验证通过后即可设置新密码。新密码生效后，请使用新密码重新登录。",
                    "你正在重置密码，验证码为：" + code + "。\n\n验证码将在 " + ttlMinutes
                            + " 分钟后失效，请勿向任何人泄露。若非本人操作，请忽略此邮件。"
            );
        };
    }

    private record EmailTemplateData(
            String brandName,
            String brandColor,
            String subject,
            String preheader,
            String title,
            String intro,
            String code,
            long ttlMinutes,
            String nextStep,
            String plainText) {
    }

    public record EmailContent(String subject, String plainText, String htmlText) {
    }
}
