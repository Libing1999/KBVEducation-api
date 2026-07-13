package com.kbv.education.dto.auth;

import com.kbv.education.entity.enums.RoleType;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        RoleType role,
        AuthUserResponse user
) {
}
