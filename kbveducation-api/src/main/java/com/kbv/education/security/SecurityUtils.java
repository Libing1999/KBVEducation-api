package com.kbv.education.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Static helpers for reading the currently authenticated principal from the
 * Spring Security context.
 *
 * <p>Phase-1 convention: the JWT subject (and therefore the authentication
 * name) is the user's UUID. This lets us resolve the acting user id without
 * hitting the database. A richer {@code UserPrincipal} is introduced with the
 * authentication filter in Step 4.</p>
 */
@Slf4j
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return the id of the authenticated user, or {@code null} when the
     * request is unauthenticated (anonymous, system tasks, migrations).
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        // Fallback: the authentication name is expected to be the user UUID.
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
