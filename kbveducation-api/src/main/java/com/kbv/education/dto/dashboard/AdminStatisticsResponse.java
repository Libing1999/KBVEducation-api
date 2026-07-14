package com.kbv.education.dto.dashboard;

/** Aggregated cards for the admin activity dashboard. */
public record AdminStatisticsResponse(
        long todayReflections,
        long todayPractice,
        long pendingReviews,
        long approvedSessions,
        long rejectedSessions,
        long activeStudents,
        long weeklyActivity,
        long monthlyActivity
) {
}
