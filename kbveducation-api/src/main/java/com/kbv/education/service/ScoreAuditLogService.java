package com.kbv.education.service;

import com.kbv.education.dto.audit.ScoreAuditLogResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.ScoreAuditEntityType;

import java.util.UUID;

public interface ScoreAuditLogService {

    /** Records a score-related change. Never throws — a logging failure must not roll back the primary write. */
    void record(ScoreAuditEntityType entityType, UUID entityId, UUID studentId,
                String action, String previousValue, String newValue, String reason);

    PageResponse<ScoreAuditLogResponse> list(ScoreAuditEntityType entityType, UUID studentId, int page, int size);
}
