package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single active row of admin-configurable branding/locale/upload/security/
 * feature-toggle settings — mirrors {@link ScoreConfig}'s single-active-row
 * pattern exactly. {@code leaderboardEnabled} is deliberately NOT duplicated
 * here; it already lives on {@link ScoreConfig} from Phase 4.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "system_settings")
public class SystemSettings extends BaseEntity {

    @Column(name = "application_name", nullable = false, length = 150)
    private String applicationName = "KBV Education";

    @Column(name = "institution_name", nullable = false, length = 150)
    private String institutionName = "KBV Education";

    @Column(name = "logo_path", columnDefinition = "text")
    private String logoPath;

    @Column(name = "primary_color_hex", nullable = false, length = 7)
    private String primaryColorHex = "#1B3A6B";

    @Column(name = "secondary_color_hex", nullable = false, length = 7)
    private String secondaryColorHex = "#F2F6FA";

    @Column(name = "accent_color_hex", nullable = false, length = 7)
    private String accentColorHex = "#C4972A";

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "UTC";

    @Column(name = "date_format", nullable = false, length = 20)
    private String dateFormat = "yyyy-MM-dd";

    @Column(name = "max_file_size_mb", nullable = false)
    private int maxFileSizeMb = 25;

    @Column(name = "allowed_file_types", nullable = false, length = 255)
    private String allowedFileTypes = "pdf,doc,docx,jpg,jpeg,png,mp3,m4a,webm,mp4";

    @Column(name = "max_login_attempts", nullable = false)
    private int maxLoginAttempts = 5;

    @Column(name = "password_min_length", nullable = false)
    private int passwordMinLength = 8;

    @Column(name = "password_require_uppercase", nullable = false)
    private boolean passwordRequireUppercase = false;

    @Column(name = "password_require_lowercase", nullable = false)
    private boolean passwordRequireLowercase = false;

    @Column(name = "password_require_digit", nullable = false)
    private boolean passwordRequireDigit = false;

    @Column(name = "password_require_special", nullable = false)
    private boolean passwordRequireSpecial = false;

    /** Maps to the refresh-token lifetime (decision #8) — the access token's lifetime stays a fixed internal detail. */
    @Column(name = "session_timeout_minutes", nullable = false)
    private int sessionTimeoutMinutes = 10080;

    @Column(name = "maintenance_mode", nullable = false)
    private boolean maintenanceMode = false;

    @Column(name = "certificate_enabled", nullable = false)
    private boolean certificateEnabled = true;

    @Column(name = "export_enabled", nullable = false)
    private boolean exportEnabled = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
