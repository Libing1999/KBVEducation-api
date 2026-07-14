package com.kbv.education.scheduler;

import com.kbv.education.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly safety net. Leaderboard snapshots are kept fresh by score/tier
 * change hooks (see {@code LeaderboardService.regenerateForStudent}), but
 * this guards against drift from any path that doesn't trigger one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardRefreshScheduler {

    private final LeaderboardService leaderboardService;

    @Scheduled(cron = "${app.leaderboard.refresh-cron:0 0 2 * * *}")
    public void refreshAll() {
        leaderboardService.regenerateAll();
        log.info("Nightly leaderboard refresh complete");
    }
}
