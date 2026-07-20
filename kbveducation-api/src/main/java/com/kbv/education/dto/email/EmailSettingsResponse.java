package com.kbv.education.dto.email;

/**
 * Admin view of the SMTP configuration. The password is never returned —
 * only whether one is stored ({@code passwordSet}).
 */
public record EmailSettingsResponse(
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        boolean passwordSet,
        String senderName,
        String senderEmail,
        boolean useTls,
        boolean useSsl,
        boolean configured
) {
}
