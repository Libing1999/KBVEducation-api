package com.kbv.education.service.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Low-level SMTP send. The {@link JavaMailSenderImpl} is built per send from
 * the current effective configuration rather than injected as a singleton —
 * admins can change SMTP settings at runtime, and a static auto-configured
 * sender would keep using boot-time values. Plain SMTP handles delivery to
 * any recipient provider (Gmail, Outlook, Yahoo, …); no per-provider logic
 * exists or is needed. Throws on failure — callers decide whether that is
 * swallowed (notifications) or surfaced (the admin test button).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final EmailSettingsService emailSettingsService;

    /** True when an SMTP host is known from the DB settings or the environment. */
    public boolean isConfigured() {
        return emailSettingsService.effectiveConfig().configured();
    }

    public void send(String recipient, String subject, String html) throws Exception {
        SmtpConfig config = emailSettingsService.effectiveConfig();
        if (!config.configured()) {
            throw new IllegalStateException("No SMTP host configured");
        }

        JavaMailSenderImpl sender = buildSender(config);
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        String senderEmail = config.senderEmail() != null ? config.senderEmail() : "no-reply@kbv.edu";
        helper.setFrom(new InternetAddress(senderEmail, config.senderName(), StandardCharsets.UTF_8.name()));
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(html, true);
        sender.send(message);
        log.info("Email '{}' sent to {}", subject, recipient);
    }

    private JavaMailSenderImpl buildSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (config.hasAuth()) {
            sender.setUsername(config.username());
            sender.setPassword(config.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(config.hasAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.useTls()));
        props.put("mail.smtp.ssl.enable", String.valueOf(config.useSsl()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(READ_TIMEOUT_MS));
        return sender;
    }
}
