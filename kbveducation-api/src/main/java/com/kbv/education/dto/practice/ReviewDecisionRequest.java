package com.kbv.education.dto.practice;

import jakarta.validation.constraints.Size;

/**
 * Admin approve/reject of a practice session, with an optional comment and, when the
 * decision differs from the AI's recommendation, an optional one-line reason.
 */
public record ReviewDecisionRequest(
        String comment,
        @Size(max = 500) String overrideReason
) {
}
