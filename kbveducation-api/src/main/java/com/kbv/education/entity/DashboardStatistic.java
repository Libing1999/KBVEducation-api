package com.kbv.education.entity;

import com.kbv.education.entity.enums.StatScope;
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

import java.time.LocalDate;

/**
 * A cached daily statistic. {@code metric} is a free-text key (no DB
 * constraint) so new metrics — including future AI-derived signals — can be
 * stored without a schema change. {@code student} is null for GLOBAL scope.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "dashboard_statistics")
public class DashboardStatistic extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private StatScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private User student;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "metric", nullable = false, length = 50)
    private String metric;

    @Column(name = "value", nullable = false)
    private int value = 0;
}
