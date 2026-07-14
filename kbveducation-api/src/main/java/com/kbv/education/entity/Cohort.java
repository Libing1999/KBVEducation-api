package com.kbv.education.entity;

import com.kbv.education.entity.enums.CohortStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A cohort (course intake). Created and managed by admins; students are
 * assigned via {@link StudentCohort}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cohorts")
public class Cohort extends BaseEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CohortStatus status = CohortStatus.UPCOMING;

    @Column(name = "max_students", nullable = false)
    private int maxStudents;
}
