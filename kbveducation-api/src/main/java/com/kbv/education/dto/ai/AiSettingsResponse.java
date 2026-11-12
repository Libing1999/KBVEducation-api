package com.kbv.education.dto.ai;

import com.kbv.education.entity.enums.AiCallMode;
import com.kbv.education.entity.enums.AiEffort;
import com.kbv.education.entity.enums.AiProvider;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The admin Settings screen's AI section. {@code claudeConfigured} / {@code geminiConfigured}
 * only say whether a key is saved on the server; the keys themselves are never returned.
 */
public record AiSettingsResponse(
        boolean aiEnabled,
        AiProvider practiceProvider,
        AiProvider reflectionProvider,
        String claudeModel,
        String geminiModel,
        String geminiTranscribeModel,
        AiEffort claudeEffort,
        AiCallMode callMode,
        boolean backupEnabled,
        LocalTime nightlyRunTime,
        String runTimezone,
        boolean autoApproveEnabled,
        int autoApproveMinRating,
        boolean autoApproveRequireNoFlags,
        int spotCheckPercent,
        int greenMinRating,
        int amberMinRating,
        int needsMeDaysWithoutSession,
        DayOfWeek weeklyReviewDay,
        BigDecimal monthlySpendLimitUsd,
        LocalDate lastRunDate,
        Instant analyseFrom,
        boolean claudeConfigured,
        boolean geminiConfigured
) {
}
