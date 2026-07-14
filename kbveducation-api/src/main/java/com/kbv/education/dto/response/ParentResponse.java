package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record ParentResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        StudentRef student,
        Instant lastLoginAt,
        Instant createdAt
) {
    /** Slim reference to the linked student (null if not linked). */
    public record StudentRef(UUID id, String firstName, String lastName, String email) {
    }
}
