package com.kbv.education.dto.auth;

import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;

import java.util.UUID;

/** Minimal, safe user representation returned by auth endpoints. */
public record AuthUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        RoleType role,
        UserStatus status
) {
}
