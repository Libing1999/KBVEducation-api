package com.kbv.education.entity.enums;

/**
 * Review status of a practice session. Default is {@code PENDING_REVIEW}.
 * A future AI validation service may set these automatically.
 */
public enum PracticeStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
