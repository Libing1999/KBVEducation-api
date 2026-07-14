package com.kbv.education.repository;

import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.enums.PracticeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID>,
        JpaSpecificationExecutor<PracticeSession> {

    Optional<PracticeSession> findByIdAndDeletedFalse(UUID id);

    List<PracticeSession> findByStudent_IdAndDeletedFalseOrderByStudyDateDesc(UUID studentId);

    long countByStudent_IdAndDeletedFalse(UUID studentId);

    long countByStudent_IdAndStudyDateBetweenAndDeletedFalse(UUID studentId, LocalDate from, LocalDate to);

    long countByStatusAndDeletedFalse(PracticeStatus status);

    // --- admin statistics ---
    long countByStudyDateAndDeletedFalse(LocalDate date);

    long countByStudyDateBetweenAndDeletedFalse(LocalDate from, LocalDate to);

    // --- distinct practice days (a student can log multiple sessions per day) ---
    @Query("select count(distinct p.studyDate) from PracticeSession p where p.student.id = :sid and p.deleted = false")
    long countDistinctStudyDays(@Param("sid") UUID studentId);

    @Query("select count(distinct p.studyDate) from PracticeSession p "
            + "where p.student.id = :sid and p.deleted = false and p.studyDate between :from and :to")
    long countDistinctStudyDaysBetween(@Param("sid") UUID studentId,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct p.studyDate from PracticeSession p where p.student.id = :sid and p.deleted = false")
    List<LocalDate> practiceDates(@Param("sid") UUID studentId);
}
