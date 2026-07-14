package com.kbv.education.dto.studyday;

import jakarta.validation.constraints.NotBlank;

public record VoidStudyDayRequest(
        @NotBlank String reason
) {
}
