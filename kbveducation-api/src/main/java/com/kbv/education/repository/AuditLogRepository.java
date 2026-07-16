package com.kbv.education.repository;

import com.kbv.education.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfterAndDeletedFalse(Instant from);
}
