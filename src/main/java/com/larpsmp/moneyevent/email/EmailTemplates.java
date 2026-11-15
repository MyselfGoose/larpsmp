package com.larpsmp.moneyevent.email;

import com.larpsmp.moneyevent.auth.EmailChallengePurpose;
import java.util.Locale;
import java.util.Objects;

/**
 * Professional HTML + plaintext bodies for auth verification emails.
 */
public final class EmailTemplates {

    private EmailTemplates() {
    }

    public static EmailMessage verificationCode(
            String toEmail,
            EmailChallengePurpose purpose,
            String code,
            int ttlMinutes
    ) {
        Objects.requireNonNull(toEmail, "toEmail");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(code, "code");

        String subject = switch (purpose) {
            case SIGNUP_VERIFY -> "Confirm your LarpSMP account";
            case PASSWORD_RESET -> "Reset your LarpSMP password";
            case USERNAME_RECOVERY -> "Recover your LarpSMP username";
        };

        String headline = switch (purpose) {
            case SIGNUP_VERIFY -> "Verify your email";
            case PASSWORD_RESET -> "Password reset code";
            case USERNAME_RECOVERY -> "Username recovery code";
        };

        String intro = switch (purpose) {
            case SIGNUP_VERIFY ->
                    "Use this code in Minecraft to approve your LarpSMP account and join the server.";
            case PASSWORD_RESET ->
                    "Use this code in Minecraft to continue resetting your LarpSMP password.";
            case USERNAME_RECOVERY ->
                    "Use this code in Minecraft to view the username linked to this email.";
        };

        String html = htmlDocument(headline, intro, code, ttlMinutes);
        String text = textDocument(headline, intro, code, ttlMinutes);
        return new EmailMessage(toEmail, subject, html, text);
    }

    private static String htmlDocument(String headline, String intro, String code, int ttlMinutes) {
        String safeCode = escapeHtml(code);
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#0f1419;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#e8e6e3;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#0f1419;padding:32px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#1a222c;border:1px solid #2d3844;border-radius:12px;overflow:hidden;">
                          <tr>
                            <td style="padding:28px 32px 12px 32px;background:linear-gradient(135deg,#1a222c 0%%,#243040 100%%);">
                              <div style="font-size:13px;letter-spacing:0.18em;text-transform:uppercase;color:#d4a017;font-weight:700;">LarpSMP</div>
                              <h1 style="margin:12px 0 0 0;font-size:24px;line-height:1.3;color:#f4f1ea;font-weight:650;">%s</h1>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 24px 32px;">
                              <p style="margin:0 0 20px 0;font-size:15px;line-height:1.55;color:#c8c4bc;">%s</p>
                              <div style="text-align:center;margin:28px 0;">
                                <div style="display:inline-block;padding:16px 28px;border-radius:10px;background:#0f1419;border:1px solid #3a4654;">
                                  <span style="font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:32px;letter-spacing:0.35em;color:#f4f1ea;font-weight:700;">%s</span>
                                </div>
                              </div>
                              <p style="margin:0;font-size:13px;line-height:1.5;color:#8b938c;">
                                This code expires in <strong style="color:#c8c4bc;">%d minutes</strong>.
                                If you did not request this, you can ignore this email — your account stays secure.
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 32px 24px 32px;border-top:1px solid #2d3844;">
                              <p style="margin:0;font-size:12px;line-height:1.45;color:#6b736c;">
                                Sent by LarpSMP authentication. Do not share this code with anyone.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(headline), escapeHtml(headline), escapeHtml(intro), safeCode, ttlMinutes);
    }

    private static String textDocument(String headline, String intro, String code, int ttlMinutes) {
        return """
                LarpSMP — %s

                %s

                Your code: %s

                This code expires in %d minutes.
                If you did not request this, you can ignore this email.

                Do not share this code with anyone.
                """.formatted(headline, intro, code, ttlMinutes).stripTrailing() + "\n";
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        String maskedLocal = local.length() <= 2
                ? local.charAt(0) + "*"
                : local.charAt(0) + "*".repeat(Math.min(local.length() - 2, 4)) + local.charAt(local.length() - 1);
        return maskedLocal + "@" + domain;
    }
}
