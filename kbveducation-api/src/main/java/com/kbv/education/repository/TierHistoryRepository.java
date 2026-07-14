package com.kbv.education.repository;

import com.kbv.education.entity.TierHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TierHistoryRepository extends JpaRepository<TierHistory, UUID> {

    Optional<TierHistory> findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    Optional<TierHistory> findFirstByStudent_IdAndConfirmedTierIsNotNullAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    Page<TierHistory> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId, Pageable pageable);
}
