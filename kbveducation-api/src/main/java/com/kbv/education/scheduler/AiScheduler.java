package com.kbv.education.scheduler;

import com.kbv.education.service.ai.AiEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Wakes the AI engine every minute: it collects finished batches and starts the nightly
 * run once the configured time (Dubai by default) has passed. The run time is a setting
 * the admin changes in the panel, so it is checked on each tick rather than fixed in a cron.
 */
@Component
@RequiredArgsConstructor
public class AiScheduler {

    private final AiEngine aiEngine;

    @Scheduled(fixedDelayString = "${app.ai.tick-ms:60000}", initialDelayString = "${app.ai.initial-delay-ms:30000}")
    public void tick() {
        aiEngine.tick();
    }
}
