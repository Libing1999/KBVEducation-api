package com.kbv.education.dto.quiz;

import com.kbv.education.entity.enums.AttemptStatus;

import java.time.Instant;
import java.util.UUID;

/** Summary of a quiz attempt (admin viewer + a student's own attempts). */
public record QuizAttemptSummary(
        UUID id,
        UUID quizId,
        String quizTitle,
        UUID lessonId,
        String lessonTitle,
        UUID studentId,
        String studentName,
        AttemptStatus status,
        Integer score,
        Integer maxScore,
        Instant submittedAt
) {
}
