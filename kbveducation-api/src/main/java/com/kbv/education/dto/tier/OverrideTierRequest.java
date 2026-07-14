package com.kbv.education.dto.tier;

import jakarta.validation.constraints.NotBlank;

public record OverrideTierRequest(
        @NotBlank String tierName,
        @NotBlank String reason
) {
}
