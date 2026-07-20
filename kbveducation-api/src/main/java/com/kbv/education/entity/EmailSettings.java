package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single active row of admin-configurable SMTP settings — same
 * single-active-row pattern as {@link SystemSettings}. Every field is
 * nullable: a blank value falls back to the {@code spring.mail.*}
 * environment configuration, so env-only deployments work with an untouched
 * row. {@code smtpPassword} holds the AES-GCM-encrypted value (see
 * SecretCipher), never the plaintext.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "email_settings")
public class EmailSettings extends BaseEntity {

    @Column(name = "smtp_host", length = 255)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;

    @Column(name = "smtp_password", columnDefinition = "text")
    private String smtpPassword;

    @Column(name = "sender_name", length = 150)
    private String senderName;

    @Column(name = "sender_email", length = 255)
    private String senderEmail;

    @Column(name = "use_tls", nullable = false)
    private boolean useTls = true;

    @Column(name = "use_ssl", nullable = false)
    private boolean useSsl = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
