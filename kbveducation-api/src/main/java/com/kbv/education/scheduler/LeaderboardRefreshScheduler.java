package com.kbv.education.scheduler;

import com.kbv.education.service.AnalyticsService;
import com.kbv.education.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly safety net. Leaderboard and per-cohort analytics are kept fresh by
 * score/tier change hooks (see {@code LeaderboardService.regenerateForStudent},
 * which also refreshes that cohort's analytics), but this guards against
 * drift from any path that doesn't trigger one, and refreshes the one thing
 * those hooks never touch: the platform-wide (cohortId = null) analytics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardRefreshScheduler {

    private final LeaderboardService leaderboardService;
    private final AnalyticsService analyticsService;

    @Scheduled(cron = "${app.leaderboard.refresh-cron:0 0 2 * * *}")
    public void refreshAll() {
        leaderboardService.regenerateAll();
        analyticsService.refresh(null);
        log.info("Nightly leaderboard + analytics refresh complete");
    }
}
