package com.kbv.education.dto.dashboard;

import java.util.UUID;

/**
 * Progress statistics for a student. No composite score is calculated (Phase 4).
 * {@code currentMonth} and {@code courseTotal} carry the same metric shape.
 */
public record StudentProgressResponse(
        UUID studentId,
        String studentName,
        ProgressMetrics currentMonth,
        ProgressMetrics courseTotal,
        int reflectionStreak,
        int practiceStreak
) {
}
