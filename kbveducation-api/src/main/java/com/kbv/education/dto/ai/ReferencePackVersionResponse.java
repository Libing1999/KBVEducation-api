package com.kbv.education.dto.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One reference pack version. {@code files} is filled only when a single version is requested. */
public record ReferencePackVersionResponse(
        UUID id,
        int versionNo,
        String note,
        boolean active,
        long fileCount,
        Instant createdAt,
        List<ReferencePackFileResponse> files
) {

    public record ReferencePackFileResponse(UUID id, String fileName, String content, int sizeBytes) {
    }
}
