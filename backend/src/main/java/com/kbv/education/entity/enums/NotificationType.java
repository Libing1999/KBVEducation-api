package com.kbv.education.entity.enums;

/**
 * Notification categories. Stored as free text (no DB constraint), so new
 * values are additive and need no migration.
 */
public enum NotificationType {
    // To students
    NEW_LESSON_PUBLISHED,
    QUIZ_AVAILABLE,
    HOMEWORK_DUE_TOMORROW,   // homework reminder
    QUIZ_REMINDER,           // Phase 3 — uncompleted quiz reminder
    REVIEW_APPROVED,         // Phase 3 — practice review outcome
    REVIEW_REJECTED,         // Phase 3
    // To admins
    HOMEWORK_SUBMITTED,
    QUIZ_SUBMITTED,
    REFLECTION_SUBMITTED,    // Phase 3
    PRACTICE_SUBMITTED,      // Phase 3
    REVIEW_REQUESTED         // Phase 3 — student asked for re-review
}
