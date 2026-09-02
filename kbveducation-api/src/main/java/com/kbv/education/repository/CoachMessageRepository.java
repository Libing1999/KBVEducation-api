package com.kbv.education.repository;

import com.kbv.education.entity.CoachMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoachMessageRepository extends JpaRepository<CoachMessage, UUID> {

    Page<CoachMessage> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<CoachMessage> findByTargetStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    List<CoachMessage> findByTargetCohort_IdAndDeletedFalseOrderByCreatedAtDesc(UUID cohortId);
}
