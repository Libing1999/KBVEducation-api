package com.kbv.education.dto.request;

import com.kbv.education.entity.enums.CohortStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCohortRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 5000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalDate examDate,
        @NotNull CohortStatus status,
        @PositiveOrZero int maxStudents
) {
}
