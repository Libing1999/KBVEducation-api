package com.kbv.education.dto.tier;

import java.util.List;

public record CurrentTierResponse(
        String calculatedTier,
        String confirmedTier,
        boolean isOverride,
        String nextPossibleTier,
        List<RemainingRequirement> remainingRequirements
) {
    public record RemainingRequirement(String metric, double current, double required) {
    }
}
