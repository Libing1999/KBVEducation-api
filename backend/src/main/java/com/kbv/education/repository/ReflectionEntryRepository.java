package com.kbv.education.repository;

import com.kbv.education.entity.ReflectionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReflectionEntryRepository extends JpaRepository<ReflectionEntry, UUID>,
        JpaSpecificationExecutor<ReflectionEntry> {

    Optional<ReflectionEntry> findByIdAndDeletedFalse(UUID id);

    Optional<ReflectionEntry> findByStudent_IdAndReflectionDateAndDeletedFalse(UUID studentId, LocalDate date);

    boolean existsByStudent_IdAndReflectionDateAndDeletedFalse(UUID studentId, LocalDate date);

    List<ReflectionEntry> findByStudent_IdAndDeletedFalseOrderByReflectionDateDesc(UUID studentId);

    long countByStudent_IdAndDeletedFalse(UUID studentId);

    long countByStudent_IdAndReflectionDateBetweenAndDeletedFalse(UUID studentId, LocalDate from, LocalDate to);

    // --- admin statistics ---
    long countByReflectionDateAndDeletedFalse(LocalDate date);

    long countByReflectionDateBetweenAndDeletedFalse(LocalDate from, LocalDate to);

    // --- streaks ---
    @Query("select r.reflectionDate from ReflectionEntry r where r.student.id = :sid and r.deleted = false")
    List<LocalDate> reflectionDates(@Param("sid") UUID studentId);
}
