package com.kbv.education.dto.scoreconfig;

import com.kbv.education.entity.enums.LeaderboardSortField;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateScoreConfigRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal practiceWeight,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal reflectionWeight,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal homeworkWeight,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal quizWeight,
        LocalDate practiceWindowStart,
        LocalDate reflectionWindowStart,
        LocalDate reflectionWindowEnd,
        @NotNull @Min(0) Integer totalReflectionDays,
        @NotNull @Min(0) Integer totalHomeworkCount,
        @NotNull Boolean leaderboardEnabled,
        @NotNull LeaderboardSortField leaderboardSortBy,
        @NotNull Boolean dashboardWidgetsEnabled
) {
}
