package com.kbv.education.entity;

import com.kbv.education.entity.enums.ScoreTriggerReason;
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

/**
 * Append-only composite-score calculation history for a student. The row
 * with {@link #current} = true is the student's current score; every prior
 * calculation is kept for "Score History" / trend charts.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "student_scores")
public class StudentScore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "practice_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal practicePercentage;

    @Column(name = "reflection_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal reflectionPercentage;

    @Column(name = "homework_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal homeworkPercentage;

    @Column(name = "quiz_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal quizPercentage;

    @Column(name = "composite_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal compositeScore;

    @Column(name = "practice_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal practiceWeight;

    @Column(name = "reflection_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal reflectionWeight;

    @Column(name = "homework_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal homeworkWeight;

    @Column(name = "quiz_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal quizWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 30)
    private ScoreTriggerReason triggerReason;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;
}
