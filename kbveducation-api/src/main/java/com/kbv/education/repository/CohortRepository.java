package com.kbv.education.repository;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.enums.CohortStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<Cohort, UUID>, JpaSpecificationExecutor<Cohort> {

    Optional<Cohort> findByIdAndDeletedFalse(UUID id);

    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(CohortStatus status);

    /** Cumulative counts as-of a point in time — power the admin dashboard's growth sparklines. */
    long countByCreatedAtBeforeAndDeletedFalse(Instant before);

    long countByStatusAndCreatedAtBeforeAndDeletedFalse(CohortStatus status, Instant before);

    List<Cohort> findTop5ByDeletedFalseOrderByCreatedAtDesc();

    List<Cohort> findByStatusAndDeletedFalse(CohortStatus status);

    @Query("select c from Cohort c where c.deleted = false and lower(c.name) like lower(concat('%', :q, '%'))")
    List<Cohort> search(@Param("q") String query, Pageable pageable);
}
