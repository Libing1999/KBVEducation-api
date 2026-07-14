package com.kbv.education.dto.practice;

import com.kbv.education.entity.enums.ReviewRequestStatus;

import java.time.Instant;
import java.util.UUID;

/** One entry in a practice session's review-request history. */
public record ReviewRequestResponse(
        UUID id,
        ReviewRequestStatus status,
        String reason,
        String adminNotes,
        String resolvedByName,
        Instant resolvedAt,
        Instant createdAt
) {
}
