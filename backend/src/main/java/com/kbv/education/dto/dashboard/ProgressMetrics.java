package com.kbv.education.dto.dashboard;

/** A set of activity counts for a period (current month or course total). */
public record ProgressMetrics(
        long reflectionDays,
        long practiceDays,
        long homeworkSubmitted,
        long quizzesCompleted,
        long lessonsCompleted
) {
}
