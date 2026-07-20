package com.kbv.education.repository;

import com.kbv.education.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {
}
