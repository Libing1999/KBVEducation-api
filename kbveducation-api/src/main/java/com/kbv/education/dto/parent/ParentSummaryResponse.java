package com.kbv.education.dto.parent;

import java.util.UUID;

/**
 * Weekly summary payload for the Parent screen — replaces the parent's old
 * 6-tab nav with a single self-contained view. Deliberately calm: no
 * composite score, no percentages, no ranking (see the Parent UI design
 * NOTES.md). Every nullable field here is a section that is either fully
 * rendered or fully absent on the frontend — never an empty/zero state.
 */
public record ParentSummaryResponse(
        String childName,
        String cohortName,
        String weekRangeLabel,

        /** Null when nothing is currently due. */
        ActionItem action,

        /**
         * True during the cohort's first week, when "this week" numbers aren't
         * meaningful yet — {@code practice}/{@code reflection} are null in that case.
         */
        boolean justStarted,

        WeekMetric practice,
        WeekMetric reflection,

        /** Course-wide completion counts. Null total means nothing to show yet. */
        CompletionCount quizzes,
        CompletionCount homework,

        /** Null when no certificate has been issued yet. */
        CertificateInfo certificate,

        /** Display name of the student's current tier, only in the final weeks of the course. Null otherwise. */
        String tierLine,

        String cadenceText
) {
    public record ActionItem(String label, String daysLeftLabel, boolean urgent) {
    }

    /** "X of Y days" this week, plus the same shape accumulated over the whole course so far. */
    public record WeekMetric(int done, int total, int courseDone, int courseTotal) {
    }

    public record CompletionCount(int done, int total) {
    }

    /** {@code id}/{@code certificateNumber} let the frontend download it via the existing
     *  {@code GET /api/parent/certificates/{id}/download} endpoint without a second list call. */
    public record CertificateInfo(UUID id, String certificateNumber, String tierLabel) {
    }
}
