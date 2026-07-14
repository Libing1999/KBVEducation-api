package com.kbv.education.repository;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.enums.CohortStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<Cohort, UUID>, JpaSpecificationExecutor<Cohort> {

    Optional<Cohort> findByIdAndDeletedFalse(UUID id);

    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(CohortStatus status);

    List<Cohort> findTop5ByDeletedFalseOrderByCreatedAtDesc();

    List<Cohort> findByStatusAndDeletedFalse(CohortStatus status);
}
