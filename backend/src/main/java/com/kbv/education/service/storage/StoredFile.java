package com.kbv.education.service.storage;

/**
 * Metadata for a file that has been persisted to storage. The {@code storedName}
 * is the unique on-disk name; the {@code originalName} is what the user uploaded.
 */
public record StoredFile(
        String storedName,
        String originalName,
        String contentType,
        long size
) {
}
