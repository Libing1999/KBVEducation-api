package com.kbv.education.entity;

import com.kbv.education.entity.enums.LeaderboardSortField;
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
 * Cached, per-cohort, per-sort-metric leaderboard ranking. Regenerated
 * (delete + bulk insert) whenever a cohort member's score changes, rather
 * than computed live on every read.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "leaderboard_snapshot")
public class LeaderboardSnapshot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "composite_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal compositeScore;

    @Column(name = "practice_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal practicePercentage;

    @Column(name = "reflection_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal reflectionPercentage;

    @Column(name = "homework_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal homeworkPercentage;

    @Column(name = "quiz_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal quizPercentage;

    @Column(name = "current_tier", length = 30)
    private String currentTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_by", nullable = false, length = 20)
    private LeaderboardSortField sortBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();
}
