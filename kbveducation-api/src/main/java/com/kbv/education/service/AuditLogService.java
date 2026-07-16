package com.kbv.education.service;

import com.kbv.education.dto.audit.AuditTrailResponse;
import com.kbv.education.dto.response.PageResponse;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogService {

    /** Records a cross-cutting audit event. Never throws — logging must not roll back the primary write. */
    void record(String action, String entityType, UUID entityId, String actorEmailSnapshot,
                String oldValue, String newValue, String ipAddress, String userAgent);

    PageResponse<AuditTrailResponse> list(UUID actorId, String action, String entityType,
                                           Instant from, Instant to, int page, int size);

    long countToday();
}
