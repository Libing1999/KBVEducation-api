package com.kbv.education.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Assign a student to a cohort. */
public record AssignCohortRequest(
        @NotNull UUID cohortId
) {
}
