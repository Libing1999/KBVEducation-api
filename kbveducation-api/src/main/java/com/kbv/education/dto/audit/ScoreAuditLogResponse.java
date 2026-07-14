package com.kbv.education.dto.audit;

import com.kbv.education.entity.enums.ScoreAuditEntityType;

import java.time.Instant;
import java.util.UUID;

public record ScoreAuditLogResponse(
        UUID id,
        ScoreAuditEntityType entityType,
        UUID entityId,
        UUID studentId,
        String studentName,
        String action,
        String previousValue,
        String newValue,
        String reason,
        UUID performedBy,
        Instant createdAt
) {
}
