package com.kbv.education.dto.file;

import java.time.Instant;
import java.util.UUID;

/** Safe representation of a stored file (no on-disk path exposed). */
public record FileResponse(
        UUID id,
        String fileName,
        String fileType,
        Long fileSize,
        Instant uploadedDate
) {
}
