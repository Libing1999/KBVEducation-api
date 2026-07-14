package com.kbv.education.repository;

import com.kbv.education.entity.StudyDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyDayRepository extends JpaRepository<StudyDay, UUID> {

    Optional<StudyDay> findByStudent_IdAndStudyDateAndDeletedFalse(UUID studentId, LocalDate studyDate);

    List<StudyDay> findByStudent_IdAndStudyDateBetweenAndDeletedFalseOrderByStudyDateAsc(
            UUID studentId, LocalDate from, LocalDate to);

    long countByStudent_IdAndDeletedFalse(UUID studentId);
}
