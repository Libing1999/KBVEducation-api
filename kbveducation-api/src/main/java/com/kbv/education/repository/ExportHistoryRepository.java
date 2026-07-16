package com.kbv.education.repository;

import com.kbv.education.entity.ExportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportHistoryRepository extends JpaRepository<ExportHistory, UUID> {

    List<ExportHistory> findTop50ByOrderByCreatedAtDesc();
}
