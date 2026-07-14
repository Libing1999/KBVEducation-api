package com.kbv.education.service;

import com.kbv.education.entity.RefreshToken;
import com.kbv.education.entity.User;

public interface RefreshTokenService {

    /** Issue a new refresh token for the user; returns the raw (unhashed) token. */
    String createToken(User user);

    /** Validate a raw refresh token, returning the active persisted record. */
    RefreshToken verify(String rawToken);

    /** Revoke the given token and issue a replacement (rotation); returns raw token. */
    String rotate(RefreshToken current, User user);

    /** Revoke all active refresh tokens for a user (logout / deactivate). */
    void revokeAllForUser(User user);
}
