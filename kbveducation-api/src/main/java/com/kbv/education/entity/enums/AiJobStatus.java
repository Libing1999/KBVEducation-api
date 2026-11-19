package com.kbv.education.entity.enums;

/**
 * Lifecycle of one AI job. QUEUED and SUBMITTED show as "being analysed";
 * NOT_ANALYSED means both AIs failed (or no AI could take the job).
 */
public enum AiJobStatus {
    QUEUED,
    SUBMITTED,
    SUCCEEDED,
    NOT_ANALYSED
}
