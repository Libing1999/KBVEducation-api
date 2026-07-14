package com.kbv.education.dto.tier;

import java.math.BigDecimal;
import java.util.UUID;

public record TierRuleResponse(
        UUID id,
        String tierName,
        int tierRank,
        BigDecimal minComposite,
        BigDecimal maxComposite,
        BigDecimal minPracticePercentage,
        int minFullPapers
) {
}
