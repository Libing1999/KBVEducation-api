package com.kbv.education.repository;

import com.kbv.education.entity.DashboardMetric;
import com.kbv.education.entity.enums.DashboardMetricKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardMetricRepository extends JpaRepository<DashboardMetric, UUID> {

    List<DashboardMetric> findByCohort_IdAndDeletedFalse(UUID cohortId);

    List<DashboardMetric> findByCohortIsNullAndDeletedFalse();

    Optional<DashboardMetric> findByCohort_IdAndMetricKeyAndDeletedFalse(UUID cohortId, DashboardMetricKey metricKey);

    Optional<DashboardMetric> findByCohortIsNullAndMetricKeyAndDeletedFalse(DashboardMetricKey metricKey);
}
