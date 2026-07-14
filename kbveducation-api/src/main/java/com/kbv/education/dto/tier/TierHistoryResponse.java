package com.kbv.education.dto.tier;

import com.kbv.education.entity.enums.TierEventSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TierHistoryResponse(
        UUID id,
        UUID studentId,
        String calculatedTier,
        String confirmedTier,
        boolean isOverride,
        String overrideReason,
        BigDecimal compositeScore,
        BigDecimal practicePercentage,
        int fullPapersCount,
        String decidedByName,
        TierEventSource source,
        Instant createdAt
) {
}
