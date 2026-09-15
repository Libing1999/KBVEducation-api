package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.RoleType;

import java.time.LocalDate;
import java.util.List;

/** Student/parent dashboard payload: composite score, breakdown, and current tier. */
public record ScoreDashboardResponse(
        String name,
        RoleType role,
        CohortInfo cohort,
        double compositeScore,
        double practicePercentage,
        double reflectionPercentage,
        double homeworkPercentage,
        double quizPercentage,
        String currentTier,
        List<LessonPlaceholder> upcomingLessons,
        List<NotificationPlaceholder> recentNotifications,
        PaceProjection pace,
        List<AttendanceDay> attendance,
        Weights weights
) {
    public record CohortInfo(String name, String status, LocalDate examDate) {
    }

    /** The active score-config weights (admin-configurable) — shown alongside each pillar so
     * "60% weight" etc. is never hardcoded display text. */
    public record Weights(double practice, double reflection, double homework, double quiz) {
    }

    public record LessonPlaceholder(String title, String scheduledFor) {
    }

    public record NotificationPlaceholder(String title, String message, String createdAt) {
    }

    /** Trailing-window pace projection — see {@code ScoreEngineService.PaceProjection}. Null when
     * the student has no active cohort (nothing meaningful to project against). */
    public record PaceProjection(double now, double atRecentPace, double last3Days,
                                  Double nextTierThreshold, String nextTierName) {
    }

    /** One calendar day in the last 30 for the consistency grid. {@code voided} days (admin-excused
     * for this student) and {@code restOrSkip} days (cohort-configured Rest/Skip Day) are both
     * excluded from the "showed up" denominator on the frontend. */
    public record AttendanceDay(LocalDate date, boolean active, boolean voided, boolean restOrSkip) {
    }
}
