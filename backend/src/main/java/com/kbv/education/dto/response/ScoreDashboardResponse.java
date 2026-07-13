package com.kbv.education.dto.response;

import com.kbv.education.entity.enums.RoleType;

import java.util.List;

/**
 * Student/parent dashboard payload. Scores are dummy values in Phase 1; the
 * shape is designed so real scoring can populate it later without contract
 * changes.
 */
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
        List<NotificationPlaceholder> recentNotifications
) {
    public record CohortInfo(String name, String status) {
    }

    public record LessonPlaceholder(String title, String scheduledFor) {
    }

    public record NotificationPlaceholder(String title, String message, String createdAt) {
    }
}
