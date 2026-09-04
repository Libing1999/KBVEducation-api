package com.kbv.education.dto.cohortday;

import com.kbv.education.entity.enums.CohortDayType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpsertCohortDayRequest(
        @NotNull LocalDate date,
        @NotNull CohortDayType dayType
) {
}
