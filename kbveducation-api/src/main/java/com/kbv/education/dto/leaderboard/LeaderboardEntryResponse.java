package com.kbv.education.dto.leaderboard;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardEntryResponse(
        int rank,
        UUID studentId,
        String studentName,
        BigDecimal compositeScore,
        String currentTier,
        BigDecimal practicePercentage,
        BigDecimal reflectionPercentage,
        BigDecimal homeworkPercentage,
        BigDecimal quizPercentage
) {
}
