package com.kbv.education.dto.dashboard;

import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.ReferenceType;

import java.time.Instant;
import java.util.UUID;

/** One entry in a student's activity timeline (newest first). */
public record ActivityLogResponse(
        UUID id,
        ActivityType type,
        String title,
        String description,
        ReferenceType referenceType,
        UUID referenceId,
        Instant occurredAt
) {
}
