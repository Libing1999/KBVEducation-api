package com.kbv.education.entity.enums;

/** Why an AI attempt counted as failed (memo 17.10.2026, section 1.4). */
public enum AiFailureReason {
    /** The AI returned an error. */
    ERROR,
    /** The answer was empty. */
    EMPTY,
    /** The answer was blocked or refused. */
    BLOCKED,
    /** The answer stopped before the end. */
    TRUNCATED,
    /** The answer was not in the fixed format, or a field failed its check. */
    INVALID_FORMAT,
    /** No credit left, or the monthly spending limit was reached. */
    NO_CREDIT,
    /** A batch was not finished in time. */
    EXPIRED,
    /** No API key is saved for this AI. */
    NOT_CONFIGURED,
    /** There was nothing to analyse (no transcript, no text). */
    NO_CONTENT;

    /** Failures worth one retry on the same AI before moving to the backup. */
    public boolean retryOnSameProvider() {
        return this == EMPTY || this == BLOCKED || this == TRUNCATED || this == INVALID_FORMAT;
    }
}
