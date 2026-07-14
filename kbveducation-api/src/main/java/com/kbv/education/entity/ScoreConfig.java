package com.kbv.education.entity;

import com.kbv.education.entity.enums.LeaderboardSortField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single active row holding the admin-configurable score-engine weights,
 * windows, and feature toggles. Only one row is {@code active} at a time
 * (DB-enforced via a partial unique index) so updates simply mutate it.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "score_config")
public class ScoreConfig extends BaseEntity {

    @Column(name = "practice_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal practiceWeight;

    @Column(name = "reflection_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal reflectionWeight;

    @Column(name = "homework_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal homeworkWeight;

    @Column(name = "quiz_weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal quizWeight;

    @Column(name = "practice_window_start")
    private LocalDate practiceWindowStart;

    @Column(name = "reflection_window_start")
    private LocalDate reflectionWindowStart;

    @Column(name = "reflection_window_end")
    private LocalDate reflectionWindowEnd;

    @Column(name = "total_reflection_days", nullable = false)
    private int totalReflectionDays;

    @Column(name = "total_homework_count", nullable = false)
    private int totalHomeworkCount;

    @Column(name = "leaderboard_enabled", nullable = false)
    private boolean leaderboardEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "leaderboard_sort_by", nullable = false, length = 20)
    private LeaderboardSortField leaderboardSortBy = LeaderboardSortField.COMPOSITE;

    @Column(name = "dashboard_widgets_enabled", nullable = false)
    private boolean dashboardWidgetsEnabled = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
