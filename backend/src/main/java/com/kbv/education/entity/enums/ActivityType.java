package com.kbv.education.entity.enums;

/**
 * Type of entry in a student's activity timeline. Stored as text with no DB
 * constraint, so new activity types can be added without a migration.
 */
public enum ActivityType {
    REFLECTION_SUBMITTED,
    PRACTICE_LOGGED,
    HOMEWORK_SUBMITTED,
    QUIZ_COMPLETED,
    REVIEW_APPROVED,
    REVIEW_REJECTED,
    REVIEW_REQUESTED
}
