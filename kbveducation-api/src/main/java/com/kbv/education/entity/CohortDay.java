package com.kbv.education.entity;

import com.kbv.education.entity.enums.CohortDayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Classifies one calendar date as a Lesson/Rest/Skip day for one specific cohort.
 * A date with no row here is a Lesson Day by default (see {@code CohortDayType}).
 * Cohort-scoped, not global — the same date may be a Rest Day for one cohort and
 * a Lesson Day for another.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "cohort_days",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cohort_day_cohort_date",
                columnNames = {"cohort_id", "date"}
        )
)
public class CohortDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 20)
    private CohortDayType dayType;
}
