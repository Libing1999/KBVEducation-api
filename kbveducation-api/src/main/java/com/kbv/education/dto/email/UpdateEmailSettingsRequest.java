package com.kbv.education.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * All fields optional: blank/null host etc. means "fall back to the
 * spring.mail.* environment configuration". A null/blank password keeps the
 * currently stored one (the UI never sees it); sending a value replaces it.
 */
public record UpdateEmailSettingsRequest(
        @Size(max = 255) String smtpHost,
        @Min(1) @Max(65535) Integer smtpPort,
        @Size(max = 255) String smtpUsername,
        @Size(max = 500) String smtpPassword,
        @Size(max = 150) String senderName,
        @Email @Size(max = 255) String senderEmail,
        boolean useTls,
        boolean useSsl
) {
}
