package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record StudentResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        CohortRef cohort,
        Instant lastLoginAt,
        Instant createdAt
) {
    /** Slim reference to the student's active cohort (null if unassigned). */
    public record CohortRef(UUID id, String name, CohortStatus status) {
    }
}
