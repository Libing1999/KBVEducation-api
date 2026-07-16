package com.kbv.education.dto.export;

import java.time.Instant;
import java.util.UUID;

public record ExportHistoryResponse(
        UUID id, String dataset, String format, Integer rowCount, String exportedByName, Instant createdAt) {
}
