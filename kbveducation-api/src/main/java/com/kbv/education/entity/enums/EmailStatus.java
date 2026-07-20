package com.kbv.education.entity.enums;

/** Lifecycle of one outbound email: queued, then sent or failed; skipped when no SMTP host is configured. */
public enum EmailStatus {
    QUEUED,
    SENT,
    FAILED,
    SKIPPED
}
