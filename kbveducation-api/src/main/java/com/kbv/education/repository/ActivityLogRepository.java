package com.kbv.education.repository;

import com.kbv.education.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    Page<ActivityLog> findByStudent_IdAndDeletedFalseOrderByOccurredAtDesc(UUID studentId, Pageable pageable);

    List<ActivityLog> findTop10ByStudent_IdAndDeletedFalseOrderByOccurredAtDesc(UUID studentId);
}
