package com.kbv.education.repository;

import com.kbv.education.entity.StudyDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyDayRepository extends JpaRepository<StudyDay, UUID> {

    Optional<StudyDay> findByStudent_IdAndStudyDateAndDeletedFalse(UUID studentId, LocalDate studyDate);

    Optional<StudyDay> findByIdAndDeletedFalse(UUID id);

    List<StudyDay> findByStudent_IdAndStudyDateBetweenAndDeletedFalseOrderByStudyDateAsc(
            UUID studentId, LocalDate from, LocalDate to);

    long countByStudent_IdAndDeletedFalse(UUID studentId);

    // --- Phase 4 scoring: voided-day-aware counts over a window ---

    long countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
            UUID studentId, LocalDate from, LocalDate to);

    long countByStudent_IdAndHasPracticeTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
            UUID studentId, LocalDate from, LocalDate to);

    long countByStudent_IdAndHasReflectionTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
            UUID studentId, LocalDate from, LocalDate to);

    // --- Phase 4 analytics: activity counts include voided days (voiding excludes a day from
    // scoring, not from the fact that the student did something) ---
    long countByStudent_IdAndHasPracticeTrueAndStudyDateBetweenAndDeletedFalse(
            UUID studentId, LocalDate from, LocalDate to);

    long countByStudent_IdAndHasReflectionTrueAndStudyDateBetweenAndDeletedFalse(
            UUID studentId, LocalDate from, LocalDate to);
}
