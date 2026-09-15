package com.kbv.education.dto.message;

import com.kbv.education.entity.enums.MessageTargetType;

import java.time.Instant;
import java.util.UUID;

/** Admin-facing view of a sent message (recent-sends list). */
public record CoachMessageResponse(
        UUID id,
        MessageTargetType targetType,
        UUID targetStudentId,
        String targetStudentName,
        UUID targetCohortId,
        String targetCohortName,
        String senderName,
        String tag,
        String body,
        Instant createdAt
) {
}
