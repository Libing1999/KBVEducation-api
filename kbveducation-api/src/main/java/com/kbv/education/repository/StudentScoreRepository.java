package com.kbv.education.repository;

import com.kbv.education.entity.StudentScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentScoreRepository extends JpaRepository<StudentScore, UUID> {

    Optional<StudentScore> findByStudent_IdAndCurrentTrueAndDeletedFalse(UUID studentId);

    Page<StudentScore> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    List<StudentScore> findByCohort_IdAndCurrentTrueAndDeletedFalse(UUID cohortId);

    List<StudentScore> findByCurrentTrueAndDeletedFalse();

    // --- Phase 4 trend charts: every recalculation in the window, not just the current row ---
    List<StudentScore> findByCohort_IdAndCreatedAtAfterAndDeletedFalse(UUID cohortId, Instant from);

    List<StudentScore> findByCreatedAtAfterAndDeletedFalse(Instant from);

    List<StudentScore> findByStudent_IdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(UUID studentId, Instant from);

    /**
     * Bulk-clears the current-row flag for a student, executed and flushed
     * immediately (rather than via entity save) so it lands before the new
     * current row is inserted — Hibernate always flushes INSERTs before
     * UPDATEs within one flush, which would otherwise trip the partial
     * unique index on (student_id) WHERE is_current.
     */
    @Modifying
    @Query("update StudentScore s set s.current = false where s.student.id = :studentId and s.current = true")
    void clearCurrent(@Param("studentId") UUID studentId);
}
