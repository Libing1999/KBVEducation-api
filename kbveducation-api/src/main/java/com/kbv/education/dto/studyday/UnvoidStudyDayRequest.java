package com.kbv.education.dto.studyday;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record UnvoidStudyDayRequest(
        @NotNull UUID studentId,
        @NotNull LocalDate date
) {
}
