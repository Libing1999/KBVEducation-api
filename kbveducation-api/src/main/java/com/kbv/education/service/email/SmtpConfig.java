package com.kbv.education.service.email;

/**
 * The resolved, ready-to-use SMTP configuration: DB settings where present,
 * spring.mail.* environment values as the fallback per field. The password
 * here is already decrypted. {@code configured()} is false when no host is
 * known from either source — sending is then skipped, never attempted.
 */
public record SmtpConfig(
        String host,
        int port,
        String username,
        String password,
        String senderName,
        String senderEmail,
        boolean useTls,
        boolean useSsl
) {
    public boolean configured() {
        return host != null && !host.isBlank();
    }

    public boolean hasAuth() {
        return username != null && !username.isBlank();
    }
}
