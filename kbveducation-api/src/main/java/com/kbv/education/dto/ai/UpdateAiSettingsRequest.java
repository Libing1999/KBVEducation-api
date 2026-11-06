package com.kbv.education.dto.ai;

import com.kbv.education.entity.enums.AiCallMode;
import com.kbv.education.entity.enums.AiEffort;
import com.kbv.education.entity.enums.AiProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record UpdateAiSettingsRequest(
        boolean aiEnabled,
        @NotNull AiProvider practiceProvider,
        @NotNull AiProvider reflectionProvider,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:/-]+") String claudeModel,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:/-]+") String geminiModel,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:/-]+") String geminiTranscribeModel,
        @NotNull AiEffort claudeEffort,
        @NotNull AiCallMode callMode,
        boolean backupEnabled,
        @NotNull LocalTime nightlyRunTime,
        @NotBlank @Size(max = 50) String runTimezone,
        boolean autoApproveEnabled,
        @Min(1) @Max(10) int autoApproveMinRating,
        boolean autoApproveRequireNoFlags,
        @Min(0) @Max(100) int spotCheckPercent,
        @Min(2) @Max(10) int greenMinRating,
        @Min(1) @Max(9) int amberMinRating,
        @Min(1) @Max(30) int needsMeDaysWithoutSession,
        @NotNull DayOfWeek weeklyReviewDay,
        @DecimalMin("0.00") BigDecimal monthlySpendLimitUsd
) {
}
