package com.kbv.education.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Link a parent to a student. */
public record LinkStudentRequest(
        @NotNull UUID studentId
) {
}
