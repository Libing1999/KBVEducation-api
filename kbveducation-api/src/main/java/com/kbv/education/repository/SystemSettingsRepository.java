package com.kbv.education.repository;

import com.kbv.education.entity.SystemSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SystemSettingsRepository extends JpaRepository<SystemSettings, UUID> {

    Optional<SystemSettings> findByActiveTrueAndDeletedFalse();
}
