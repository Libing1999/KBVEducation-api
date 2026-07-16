package com.kbv.education.entity.enums;

/**
 * The datasets the generic export registry (Phase 5 Step 3) can produce.
 * LEADERBOARD and COMPOSITE_SCORES are deliberately absent — they're already
 * served by the untouched Phase 4 {@code AdminExportController} endpoints.
 */
public enum ExportDataset {
    STUDENTS,
    PARENTS,
    COHORTS,
    LESSONS,
    HOMEWORK,
    QUIZZES,
    REFLECTIONS,
    PRACTICE_LOGS,
    ANALYTICS,
    AUDIT_LOGS
}
