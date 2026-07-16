package com.kbv.education.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditTrailResponse(
        UUID id,
        String actorName,
        String action,
        String entityType,
        UUID entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {
}
