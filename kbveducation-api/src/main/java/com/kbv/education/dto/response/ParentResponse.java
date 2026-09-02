package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ParentResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        List<StudentRef> students,
        Instant lastLoginAt,
        Instant createdAt
) {
    /** Slim reference to one linked student. */
    public record StudentRef(UUID id, String firstName, String lastName, String email) {
    }
}
