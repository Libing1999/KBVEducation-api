package com.kbv.education.repository;

import com.kbv.education.entity.CohortDay;
import com.kbv.education.entity.enums.CohortDayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortDayRepository extends JpaRepository<CohortDay, UUID> {

    /** Regardless of soft-delete state — the upsert path updates this row in place rather than
     *  risk a second row for the same (cohort, date) pair after a prior unconfigure. */
    Optional<CohortDay> findByCohort_IdAndDate(UUID cohortId, LocalDate date);

    List<CohortDay> findByCohort_IdAndDateBetweenAndDeletedFalse(UUID cohortId, LocalDate from, LocalDate to);

    long countByCohort_IdAndDayTypeInAndDateBetweenAndDeletedFalse(
            UUID cohortId, Collection<CohortDayType> dayTypes, LocalDate from, LocalDate to);
}
