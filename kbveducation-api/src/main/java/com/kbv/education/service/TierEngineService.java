package com.kbv.education.service;

import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.tier.CurrentTierResponse;
import com.kbv.education.dto.tier.TierHistoryResponse;

import java.util.UUID;

public interface TierEngineService {

    /** Recomputes the student's calculated tier from their current score and appends a SYSTEM history row. */
    TierHistoryResponse recalculateCalculatedTier(UUID studentId);

    /** Calculated + confirmed tier, next possible tier, and what's left to reach it. */
    CurrentTierResponse getCurrentTier(UUID studentId);

    /** Admin confirms the calculated tier as-is. */
    TierHistoryResponse confirm(UUID studentId, UUID adminId);

    /** Admin overrides the tier to a specific value; a reason is required. */
    TierHistoryResponse override(UUID studentId, String tierName, String reason, UUID adminId);

    PageResponse<TierHistoryResponse> history(UUID studentId, int page, int size);

    /** The tier to display for a student: confirmed/overridden if set, else the system-calculated tier. */
    default String getDisplayTier(UUID studentId) {
        CurrentTierResponse tier = getCurrentTier(studentId);
        return tier.confirmedTier() != null ? tier.confirmedTier() : tier.calculatedTier();
    }
}
