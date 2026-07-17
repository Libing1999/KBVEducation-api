package com.kbv.education.service;

import java.util.UUID;

public interface LoginAttemptService {

    /**
     * Increments the user's failed-login counter and locks the account once the
     * configured threshold is reached. Runs in its own transaction (REQUIRES_NEW)
     * so the write survives even though the caller (AuthServiceImpl.login) throws
     * an exception right after this returns, which would otherwise roll back the
     * enclosing @Transactional method and silently discard the increment.
     */
    void recordFailedAttempt(UUID userId);
}
