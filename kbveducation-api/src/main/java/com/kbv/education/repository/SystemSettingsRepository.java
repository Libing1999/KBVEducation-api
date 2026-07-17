package com.kbv.education.repository;

import com.kbv.education.entity.SystemSettings;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, UUID> {

    /** Cached (Phase 5 Step 7) — this single active row is read on nearly every request (maintenance-mode filter). Evicted on update. */
    @Cacheable("systemSettings")
    Optional<SystemSettings> findByActiveTrueAndDeletedFalse();
}
