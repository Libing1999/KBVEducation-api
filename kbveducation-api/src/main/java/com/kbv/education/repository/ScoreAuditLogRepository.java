package com.kbv.education.repository;

import com.kbv.education.entity.ScoreAuditLog;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScoreAuditLogRepository extends JpaRepository<ScoreAuditLog, UUID> {

    Page<ScoreAuditLog> findByEntityTypeAndDeletedFalseOrderByCreatedAtDesc(
            ScoreAuditEntityType entityType, Pageable pageable);

    Page<ScoreAuditLog> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    Page<ScoreAuditLog> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
}
