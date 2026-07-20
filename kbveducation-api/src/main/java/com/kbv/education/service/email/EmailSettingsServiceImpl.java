package com.kbv.education.service.email;

import com.kbv.education.dto.email.EmailSettingsResponse;
import com.kbv.education.dto.email.UpdateEmailSettingsRequest;
import com.kbv.education.entity.EmailSettings;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.EmailSettingsRepository;
import com.kbv.education.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSettingsServiceImpl implements EmailSettingsService {

    private final EmailSettingsRepository emailSettingsRepository;
    private final SecretCipher secretCipher;

    // Environment fallbacks (standard Spring Mail property names) - used for any
    // field the admin has left blank, so an env-only deployment needs no DB edits.
    @Value("${spring.mail.host:}")
    private String envHost;

    @Value("${spring.mail.port:587}")
    private int envPort;

    @Value("${spring.mail.username:}")
    private String envUsername;

    @Value("${spring.mail.password:}")
    private String envPassword;

    @Value("${app.mail.sender-name:KBV Education}")
    private String envSenderName;

    @Value("${app.mail.sender-email:}")
    private String envSenderEmail;

    @Override
    @Transactional(readOnly = true)
    public EmailSettingsResponse get() {
        return toResponse(getActiveRow());
    }

    @Override
    @Transactional
    public EmailSettingsResponse update(UpdateEmailSettingsRequest request) {
        EmailSettings settings = getActiveRow();
        settings.setSmtpHost(blankToNull(request.smtpHost()));
        settings.setSmtpPort(request.smtpPort());
        settings.setSmtpUsername(blankToNull(request.smtpUsername()));
        // Blank password = keep the stored one (the UI never round-trips it).
        if (StringUtils.hasText(request.smtpPassword())) {
            settings.setSmtpPassword(secretCipher.encrypt(request.smtpPassword()));
        }
        settings.setSenderName(blankToNull(request.senderName()));
        settings.setSenderEmail(blankToNull(request.senderEmail()));
        settings.setUseTls(request.useTls());
        settings.setUseSsl(request.useSsl());
        EmailSettings saved = emailSettingsRepository.save(settings);
        log.info("Updated email settings {}", saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SmtpConfig effectiveConfig() {
        EmailSettings s = getActiveRow();
        String host = firstNonBlank(s.getSmtpHost(), envHost);
        int port = s.getSmtpPort() != null ? s.getSmtpPort() : envPort;
        String username = firstNonBlank(s.getSmtpUsername(), envUsername);
        String password = StringUtils.hasText(s.getSmtpPassword())
                ? secretCipher.decrypt(s.getSmtpPassword())
                : envPassword;
        String senderEmail = firstNonBlank(s.getSenderEmail(), envSenderEmail, username);
        String senderName = firstNonBlank(s.getSenderName(), envSenderName);
        return new SmtpConfig(host, port, username, password, senderName, senderEmail,
                s.isUseTls(), s.isUseSsl());
    }

    private EmailSettings getActiveRow() {
        return emailSettingsRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active email settings found"));
    }

    private EmailSettingsResponse toResponse(EmailSettings s) {
        boolean passwordSet = StringUtils.hasText(s.getSmtpPassword()) || StringUtils.hasText(envPassword);
        boolean configured = StringUtils.hasText(firstNonBlank(s.getSmtpHost(), envHost));
        return new EmailSettingsResponse(
                s.getSmtpHost(), s.getSmtpPort(), s.getSmtpUsername(), passwordSet,
                s.getSenderName(), s.getSenderEmail(), s.isUseTls(), s.isUseSsl(), configured);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
