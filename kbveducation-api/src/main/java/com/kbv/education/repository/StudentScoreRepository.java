package com.kbv.education.repository;

import com.kbv.education.entity.StudentScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentScoreRepository extends JpaRepository<StudentScore, UUID> {

    Optional<StudentScore> findByStudent_IdAndCurrentTrueAndDeletedFalse(UUID studentId);

    Page<StudentScore> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    List<StudentScore> findByCohort_IdAndCurrentTrueAndDeletedFalse(UUID cohortId);

    List<StudentScore> findByCurrentTrueAndDeletedFalse();
}
