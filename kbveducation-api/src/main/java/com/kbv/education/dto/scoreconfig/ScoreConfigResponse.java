package com.kbv.education.dto.scoreconfig;

import com.kbv.education.entity.enums.LeaderboardSortField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ScoreConfigResponse(
        UUID id,
        BigDecimal practiceWeight,
        BigDecimal reflectionWeight,
        BigDecimal homeworkWeight,
        BigDecimal quizWeight,
        LocalDate practiceWindowStart,
        LocalDate reflectionWindowStart,
        LocalDate reflectionWindowEnd,
        int totalReflectionDays,
        int totalHomeworkCount,
        boolean leaderboardEnabled,
        LeaderboardSortField leaderboardSortBy,
        boolean dashboardWidgetsEnabled
) {
}
