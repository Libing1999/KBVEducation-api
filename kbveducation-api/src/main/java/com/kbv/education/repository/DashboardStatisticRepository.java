package com.kbv.education.repository;

import com.kbv.education.entity.DashboardStatistic;
import com.kbv.education.entity.enums.StatScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardStatisticRepository extends JpaRepository<DashboardStatistic, UUID> {

    Optional<DashboardStatistic> findByScopeAndStudent_IdAndStatDateAndMetricAndDeletedFalse(
            StatScope scope, UUID studentId, LocalDate statDate, String metric);

    Optional<DashboardStatistic> findByScopeAndStudentIsNullAndStatDateAndMetricAndDeletedFalse(
            StatScope scope, LocalDate statDate, String metric);

    List<DashboardStatistic> findByScopeAndStatDateAndDeletedFalse(StatScope scope, LocalDate statDate);
}
