package com.kbv.education.dto.score;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StudentScoreResponse(
        UUID id,
        UUID studentId,
        BigDecimal practicePercentage,
        BigDecimal reflectionPercentage,
        BigDecimal homeworkPercentage,
        BigDecimal quizPercentage,
        BigDecimal compositeScore,
        Instant calculatedAt
) {
}
