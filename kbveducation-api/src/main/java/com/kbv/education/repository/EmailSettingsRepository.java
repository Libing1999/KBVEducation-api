package com.kbv.education.repository;

import com.kbv.education.entity.EmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailSettingsRepository extends JpaRepository<EmailSettings, UUID> {

    Optional<EmailSettings> findByActiveTrueAndDeletedFalse();
}
