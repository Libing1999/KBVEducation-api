package com.kbv.education.dto.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record AdminAnalyticsResponse(
        BigDecimal averageComposite,
        BigDecimal highestScore,
        BigDecimal lowestScore,
        BigDecimal averagePractice,
        BigDecimal averageReflection,
        BigDecimal averageHomework,
        BigDecimal averageQuiz,
        Map<String, Long> tierDistribution,
        long activeStudents,
        long atRiskStudents,
        long weeklyActivity,
        long monthlyActivity,
        Instant computedAt
) {
}
