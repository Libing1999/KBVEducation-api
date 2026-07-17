package com.kbv.education.dto.backup;

import com.kbv.education.entity.enums.BackupStatus;

import java.time.Instant;
import java.util.UUID;

public record BackupHistoryResponse(
        UUID id,
        Long fileSizeBytes,
        BackupStatus status,
        String errorMessage,
        String createdByName,
        Instant createdAt
) {
}
