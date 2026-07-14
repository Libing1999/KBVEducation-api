package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Assignment of a student (a {@link User} with the STUDENT role) to a
 * {@link Cohort}. A student may have at most one {@code active} assignment,
 * enforced by a partial unique index.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "student_cohort",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sc_student_cohort",
                columnNames = {"student_id", "cohort_id"}
        )
)
public class StudentCohort extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
