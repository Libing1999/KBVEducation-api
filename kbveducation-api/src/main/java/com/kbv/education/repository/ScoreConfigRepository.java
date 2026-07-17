package com.kbv.education.repository;

import com.kbv.education.entity.ScoreConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreConfigRepository extends JpaRepository<ScoreConfig, UUID> {

    /** Cached (Phase 5 Step 7) — this single active row is read on nearly every scoring call. Evicted on update. */
    @Cacheable("scoreConfig")
    Optional<ScoreConfig> findByActiveTrueAndDeletedFalse();
}
