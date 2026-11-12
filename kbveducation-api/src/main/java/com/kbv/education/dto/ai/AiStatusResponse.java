package com.kbv.education.dto.ai;

import com.kbv.education.entity.enums.AiProvider;

import java.time.LocalDate;
import java.util.List;

/** At-a-glance AI status for the admin Settings screen. */
public record AiStatusResponse(
        boolean aiEnabled,
        boolean claudeConfigured,
        boolean geminiConfigured,
        boolean running,
        LocalDate lastRunDate,
        long openBatches,
        long jobsWaiting,
        long jobsNotAnalysedThisMonth,
        List<Usage> usageThisMonth
) {

    /** Token use this calendar month (UTC), from the saved results. */
    public record Usage(AiProvider provider, long results, long inputTokens, long outputTokens,
                        long cacheReadTokens, long cacheWriteTokens) {
    }
}
