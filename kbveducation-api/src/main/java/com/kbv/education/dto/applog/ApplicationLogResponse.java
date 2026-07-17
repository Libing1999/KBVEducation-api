package com.kbv.education.dto.applog;

import com.kbv.education.entity.enums.LogSeverity;

import java.time.Instant;
import java.util.UUID;

public record ApplicationLogResponse(
        UUID id,
        LogSeverity severity,
        String source,
        String message,
        String endpoint,
        String httpMethod,
        String ipAddress,
        Instant createdAt
) {
}
