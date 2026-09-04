package com.kbv.education.dto.dashboard;

import java.time.LocalDate;

/** One day on the activity calendar and which activities occurred. */
public record StudyDayResponse(
        LocalDate date,
        boolean hasReflection,
        boolean hasPractice,
        boolean hasHomework,
        boolean hasQuiz,
        boolean voided,
        String voidedReason
) {
}
