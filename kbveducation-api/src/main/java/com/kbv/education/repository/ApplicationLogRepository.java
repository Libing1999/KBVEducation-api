package com.kbv.education.repository;

import com.kbv.education.entity.ApplicationLog;
import com.kbv.education.entity.enums.LogSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationLogRepository extends JpaRepository<ApplicationLog, UUID> {

    Page<ApplicationLog> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<ApplicationLog> findBySeverityAndDeletedFalseOrderByCreatedAtDesc(LogSeverity severity, Pageable pageable);
}
