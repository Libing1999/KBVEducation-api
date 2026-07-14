package com.kbv.education.dto.practice;

import com.kbv.education.entity.enums.ReviewRequestStatus;

import java.time.Instant;
import java.util.UUID;

/** Row in the admin review-requests queue. */
public record ReviewRequestAdminSummary(
        UUID id,
        UUID practiceSessionId,
        UUID studentId,
        String studentName,
        String cohortName,
        String subject,
        String reason,
        ReviewRequestStatus status,
        Instant createdAt
) {
}
