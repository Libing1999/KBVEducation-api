package com.kbv.education.repository;

import com.kbv.education.entity.BackupHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BackupHistoryRepository extends JpaRepository<BackupHistory, UUID> {

    List<BackupHistory> findByDeletedFalseOrderByCreatedAtDesc();

    Optional<BackupHistory> findByIdAndDeletedFalse(UUID id);
}
