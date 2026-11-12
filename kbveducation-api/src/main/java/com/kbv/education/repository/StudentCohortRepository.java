package com.kbv.education.repository;

import com.kbv.education.entity.StudentCohort;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentCohortRepository extends JpaRepository<StudentCohort, UUID> {

    Optional<StudentCohort> findByStudent_IdAndActiveTrueAndDeletedFalse(UUID studentId);

    Optional<StudentCohort> findByStudent_IdAndCohort_IdAndDeletedFalse(UUID studentId, UUID cohortId);

    List<StudentCohort> findByCohort_IdAndActiveTrueAndDeletedFalse(UUID cohortId);

    long countByCohort_IdAndActiveTrueAndDeletedFalse(UUID cohortId);

    /** Every cohort link, past and current: a student who moved keeps the old cohort's homework. */
    List<StudentCohort> findByStudent_IdInAndDeletedFalse(Collection<UUID> studentIds);
}
