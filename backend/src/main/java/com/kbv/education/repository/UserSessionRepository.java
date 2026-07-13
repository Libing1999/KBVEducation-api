package com.kbv.education.repository;

import com.kbv.education.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /** Distinct users who logged in since the given instant (for the admin dashboard). */
    long countByLoginAtAfter(Instant since);
}
