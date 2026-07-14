package com.kbv.education.repository;

import com.kbv.education.entity.ScoreConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScoreConfigRepository extends JpaRepository<ScoreConfig, UUID> {

    Optional<ScoreConfig> findByActiveTrueAndDeletedFalse();
}
