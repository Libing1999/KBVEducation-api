package com.kbv.education.entity;

import com.kbv.education.entity.enums.DashboardMetricKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cached admin-analytics aggregate. {@link #cohort} is null for a
 * platform-wide (global) metric, set for a per-cohort one.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "dashboard_metrics")
public class DashboardMetric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_key", nullable = false, length = 50)
    private DashboardMetricKey metricKey;

    @Column(name = "metric_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal metricValue;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();
}
