package com.kbv.education.dto.quiz;

import java.time.Instant;
import java.util.UUID;

/**
 * Result returned after submitting a quiz. Phase 2 shows only a success
 * confirmation — no score page — though the score is stored server-side.
 */
public record QuizSubmissionResult(
        UUID attemptId,
        Instant submittedAt,
        int totalQuestions,
        int answered,
        String message
) {
}
