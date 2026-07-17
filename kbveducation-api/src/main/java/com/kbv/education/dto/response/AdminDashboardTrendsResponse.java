package com.kbv.education.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Time-series and breakdown data backing the admin dashboard's charts
 * (sparklines, the Activity Overview line chart, the Cohort Status donut,
 * and the Top Performing Students table). Every figure is derived from real
 * rows (created_at/study_date/reflection_date/submitted_at/login_at) — none
 * of it is simulated or interpolated. "Growth" series are cumulative counts
 * of *today's* matching rows bucketed by their creation day, which is an
 * honest read of "when did today's records get created", not a literal
 * historical snapshot (there's no day-by-day history table to replay).
 */
public record AdminDashboardTrendsResponse(
        List<DailyValue> studentsGrowth,
        List<DailyValue> parentsGrowth,
        List<DailyValue> cohortsGrowth,
        List<DailyValue> activeCohortsGrowth,
        List<DailyValue> loginsPerDay,
        Double studentsChangePct,
        Double parentsChangePct,
        Double cohortsChangePct,
        Double activeCohortsChangePct,
        Double loginsChangePct,
        List<ActivityDay> activityTrend,
        CohortStatusBreakdown cohortStatus,
        List<TopStudent> topStudents
) {

    public record DailyValue(LocalDate date, long value) {
    }

    public record ActivityDay(
            LocalDate date,
            long reflections,
            long practiceLogs,
            long homeworkSubmissions,
            long quizAttempts
    ) {
    }

    public record CohortStatusBreakdown(long active, long inactive, long upcoming) {
    }

    public record TopStudent(String studentName, String cohortName, double compositeScore) {
    }
}
