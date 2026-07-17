package com.kbv.education;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the KBV Education Course Companion Platform.
 *
 * <p>JPA auditing is enabled here so that audit fields
 * ({@code createdAt}, {@code updatedAt}, {@code createdBy}, {@code updatedBy})
 * are populated automatically on persisted entities. Scheduling is enabled for
 * the Phase 2 "homework due tomorrow" reminder job. Caching (Phase 5 Step 7)
 * backs the hot single-active-row {@code ScoreConfig}/{@code SystemSettings}
 * lookups.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class KbvEducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbvEducationApplication.class, args);
    }
}
