package com.kbv.education.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Per-student, per-day rollup of which activities occurred. Powers the activity
 * calendar without scanning every source table.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "study_days")
public class StudyDay extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    @Column(name = "has_reflection", nullable = false)
    private boolean hasReflection = false;

    @Column(name = "has_practice", nullable = false)
    private boolean hasPractice = false;

    @Column(name = "has_homework", nullable = false)
    private boolean hasHomework = false;

    @Column(name = "has_quiz", nullable = false)
    private boolean hasQuiz = false;
}
