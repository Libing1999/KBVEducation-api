package com.kbv.education.dto.tier;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UpsertTierRuleRequest(
        @NotNull UUID id,
        @NotBlank String tierName,
        @NotNull @Min(1) Integer tierRank,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minComposite,
        @DecimalMin("0") @DecimalMax("100") BigDecimal maxComposite,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minPracticePercentage,
        @NotNull @Min(0) Integer minFullPapers
) {
}
