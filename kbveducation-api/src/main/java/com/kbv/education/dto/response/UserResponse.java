package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        RoleType role,
        UserStatus status,
        Instant lastLoginAt,
        Instant createdAt,
        boolean locked,
        int failedLoginAttempts
) {
}
