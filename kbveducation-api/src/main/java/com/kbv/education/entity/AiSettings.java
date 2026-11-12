package com.kbv.education.entity;

import com.kbv.education.entity.enums.AiCallMode;
import com.kbv.education.entity.enums.AiEffort;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.entity.converter.LocalTimeTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Single active row of admin-configurable AI settings — same single-active-row
 * pattern as {@link SystemSettings}. API keys are never stored here: they live
 * only in the server's environment ({@code ANTHROPIC_API_KEY}, {@code GEMINI_API_KEY}).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "ai_settings")
public class AiSettings extends BaseEntity {

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_provider", nullable = false, length = 20)
    private AiProvider practiceProvider = AiProvider.CLAUDE;

    @Enumerated(EnumType.STRING)
    @Column(name = "reflection_provider", nullable = false, length = 20)
    private AiProvider reflectionProvider = AiProvider.CLAUDE;

    @Column(name = "claude_model", nullable = false, length = 100)
    private String claudeModel = "claude-sonnet-5-5";

    @Column(name = "gemini_model", nullable = false, length = 100)
    private String geminiModel = "gemini-3.8-flash";

    @Column(name = "gemini_transcribe_model", nullable = false, length = 100)
    private String geminiTranscribeModel = "gemini-3.5-transcribe";

    @Enumerated(EnumType.STRING)
    @Column(name = "claude_effort", nullable = false, length = 10)
    private AiEffort claudeEffort = AiEffort.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_mode", nullable = false, length = 10)
    private AiCallMode callMode = AiCallMode.BATCH;

    @Column(name = "backup_enabled", nullable = false)
    private boolean backupEnabled = true;

    /** Wall-clock time in {@code runTimezone}; stored as "HH:mm" text so it never shifts. */
    @Convert(converter = LocalTimeTextConverter.class)
    @Column(name = "nightly_run_time", nullable = false, length = 5)
    private LocalTime nightlyRunTime = LocalTime.of(23, 0);

    @Column(name = "run_timezone", nullable = false, length = 50)
    private String runTimezone = "Asia/Dubai";

    @Column(name = "auto_approve_enabled", nullable = false)
    private boolean autoApproveEnabled = false;

    @Column(name = "auto_approve_min_rating", nullable = false)
    private int autoApproveMinRating = 9;

    @Column(name = "auto_approve_require_no_flags", nullable = false)
    private boolean autoApproveRequireNoFlags = true;

    @Column(name = "spot_check_percent", nullable = false)
    private int spotCheckPercent = 10;

    @Column(name = "green_min_rating", nullable = false)
    private int greenMinRating = 8;

    @Column(name = "amber_min_rating", nullable = false)
    private int amberMinRating = 4;

    @Column(name = "needs_me_days_without_session", nullable = false)
    private int needsMeDaysWithoutSession = 2;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekly_review_day", nullable = false, length = 10)
    private DayOfWeek weeklyReviewDay = DayOfWeek.SUNDAY;

    @Column(name = "monthly_spend_limit_usd", precision = 10, scale = 2)
    private BigDecimal monthlySpendLimitUsd;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "analyse_from")
    private Instant analyseFrom;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public AiProvider providerFor(AiJobType type) {
        return type == AiJobType.PRACTICE_REVIEW ? practiceProvider : reflectionProvider;
    }

    public String modelFor(AiProvider provider) {
        return provider == AiProvider.CLAUDE ? claudeModel : geminiModel;
    }
}
