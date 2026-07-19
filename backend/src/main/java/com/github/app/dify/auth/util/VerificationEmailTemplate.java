package com.github.app.dify.auth.util;

import com.github.app.dify.auth.req.VerificationCodePurpose;

/**
 * Builds the multipart content used by verification-code emails.
 *
 * <p>The HTML deliberately uses a table-based layout and inline styles so it
 * remains readable in desktop, mobile and legacy email clients.</p>
 */
public final class VerificationEmailTemplate {

    private static final String HTML_TEMPLATE = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <meta name="color-scheme" content="light">
              <meta name="supported-color-schemes" content="light">
              <title>{{SUBJECT}}</title>
              <style>
                @media only screen and (max-width: 620px) {
                  .email-shell { width: 100% !important; }
                  .content-cell { padding: 32px 24px !important; }
                  .code-value { font-size: 30px !important; letter-spacing: 7px !important; }
                }
              </style>
            </head>
            <body style="margin:0;padding:0;background-color:#f5f7fa;color:#303133;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',Arial,sans-serif;">
              <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
                {{PREHEADER}}
              </div>
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;background-color:#f5f7fa;">
                <tr>
                  <td align="center" style="padding:40px 16px;">
                    <table role="presentation" class="email-shell" width="600" cellspacing="0" cellpadding="0" border="0" style="width:600px;max-width:600px;background-color:#ffffff;border:1px solid #ebeef5;border-radius:16px;box-shadow:0 12px 32px rgba(48,49,51,0.08);overflow:hidden;">
                      <tr>
                        <td style="height:6px;background-color:#409eff;font-size:0;line-height:0;">&nbsp;</td>
                      </tr>
                      <tr>
                        <td class="content-cell" style="padding:42px 48px 38px;">
                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                            <tr>
                              <td style="padding-bottom:32px;">
                                <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                                  <tr>
                                    <td width="42" height="42" align="center" valign="middle" style="width:42px;height:42px;background-color:#409eff;border-radius:12px;color:#ffffff;font-size:21px;font-weight:700;line-height:42px;">D</td>
                                    <td style="padding-left:12px;color:#303133;font-size:20px;font-weight:700;letter-spacing:0.2px;">DifyApp</td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td>
                                <span style="display:inline-block;padding:5px 10px;background-color:#ecf5ff;border-radius:999px;color:#337ecc;font-size:12px;font-weight:600;line-height:18px;">邮箱验证</span>
                                <h1 style="margin:16px 0 10px;color:#303133;font-size:28px;line-height:1.35;font-weight:700;">{{TITLE}}</h1>
                                <p style="margin:0;color:#606266;font-size:15px;line-height:1.8;">{{INTRO}}</p>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:28px 0 24px;">
                                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;background-color:#f7fbff;border:1px solid #d9ecff;border-radius:12px;">
                                  <tr>
                                    <td align="center" style="padding:22px 16px 20px;">
                                      <div style="margin-bottom:9px;color:#909399;font-size:12px;line-height:18px;letter-spacing:1px;">验证码</div>
                                      <div class="code-value" style="color:#2b6cb0;font-family:Consolas,Monaco,'Courier New',monospace;font-size:36px;font-weight:700;line-height:1.2;letter-spacing:10px;white-space:nowrap;">{{CODE}}</div>
                                      <div style="margin-top:12px;color:#606266;font-size:13px;line-height:20px;">有效期 <strong style="color:#303133;">{{TTL}} 分钟</strong></div>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:14px 16px;background-color:#fafafa;border-left:3px solid #409eff;border-radius:6px;color:#606266;font-size:13px;line-height:1.7;">
                                {{NEXT_STEP}}
                              </td>
                            </tr>
                            <tr>
                              <td style="padding-top:24px;color:#909399;font-size:12px;line-height:1.8;">
                                为保障账号安全，请勿向任何人泄露验证码。若非本人操作，请忽略此邮件，无需进行其他处理。
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td align="center" style="padding:22px 24px;background-color:#fafafa;border-top:1px solid #ebeef5;color:#a8abb2;font-size:12px;line-height:1.7;">
                          此邮件由 DifyApp 系统自动发送，请勿直接回复。
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

    private VerificationEmailTemplate() {
    }

    public static EmailContent render(VerificationCodePurpose purpose, String code, long ttlMinutes) {
        boolean isRegister = purpose == VerificationCodePurpose.REGISTER;
        String scene = isRegister ? "注册账号" : "重置密码";
        String subject = "DifyApp " + scene + "验证码";
        String title = isRegister ? "完成账号注册" : "验证身份并重置密码";
        String intro = isRegister
                ? "感谢注册 DifyApp。请在注册页面输入以下验证码，完成邮箱验证。"
                : "我们收到了你的密码重置请求。请在重置密码页面输入以下验证码。";
        String nextStep = isRegister
                ? "完成注册后，账号将进入管理员审核；审核通过后即可登录使用 DifyApp。"
                : "验证通过后即可设置新密码。新密码生效后，请使用新密码重新登录。";
        String preheader = "你的 DifyApp " + scene + "验证码是 " + code + "，"
                + ttlMinutes + " 分钟内有效。";
        String plainText = "你正在" + scene + "，验证码为：" + code + "。\n\n验证码将在 "
                + ttlMinutes + " 分钟后失效，请勿向任何人泄露。若非本人操作，请忽略此邮件。"
                + (isRegister ? "\n\n完成注册后，账号需要等待管理员审核。" : "");
        String htmlText = HTML_TEMPLATE
                .replace("{{SUBJECT}}", subject)
                .replace("{{PREHEADER}}", preheader)
                .replace("{{TITLE}}", title)
                .replace("{{INTRO}}", intro)
                .replace("{{CODE}}", code)
                .replace("{{TTL}}", String.valueOf(ttlMinutes))
                .replace("{{NEXT_STEP}}", nextStep);
        return new EmailContent(subject, plainText, htmlText);
    }

    public record EmailContent(String subject, String plainText, String htmlText) {
    }
}
