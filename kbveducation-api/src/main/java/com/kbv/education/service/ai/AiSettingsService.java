package com.kbv.education.service.ai;

import com.kbv.education.dto.ai.AiSettingsResponse;
import com.kbv.education.dto.ai.UpdateAiSettingsRequest;
import com.kbv.education.entity.AiSettings;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.AiSettingsRepository;
import com.kbv.education.service.ai.provider.ClaudeAiClient;
import com.kbv.education.service.ai.provider.GeminiAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Reads and updates the single active row of AI settings (admin Settings screen). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsRepository repository;
    private final ClaudeAiClient claude;
    private final GeminiAiClient gemini;

    @Transactional(readOnly = true)
    public AiSettings getActive() {
        return repository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active AI settings found"));
    }

    @Transactional(readOnly = true)
    public AiSettingsResponse get() {
        return toResponse(getActive());
    }

    @Transactional
    public AiSettingsResponse update(UpdateAiSettingsRequest r) {
        if (r.amberMinRating() >= r.greenMinRating()) {
            throw new BadRequestException("The amber range must start below the green range");
        }
        try {
            ZoneId.of(r.runTimezone());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + r.runTimezone());
        }
        AiSettings s = getActive();
        if (r.aiEnabled() && s.getAnalyseFrom() == null) {
            // First switch-on: only submissions from now on are analysed automatically.
            s.setAnalyseFrom(Instant.now());
        }
        s.setAiEnabled(r.aiEnabled());
        s.setPracticeProvider(r.practiceProvider());
        s.setReflectionProvider(r.reflectionProvider());
        s.setClaudeModel(r.claudeModel().trim());
        s.setGeminiModel(r.geminiModel().trim());
        s.setGeminiTranscribeModel(r.geminiTranscribeModel().trim());
        s.setClaudeEffort(r.claudeEffort());
        s.setCallMode(r.callMode());
        s.setBackupEnabled(r.backupEnabled());
        s.setNightlyRunTime(r.nightlyRunTime());
        s.setRunTimezone(r.runTimezone());
        s.setAutoApproveEnabled(r.autoApproveEnabled());
        s.setAutoApproveMinRating(r.autoApproveMinRating());
        s.setAutoApproveRequireNoFlags(r.autoApproveRequireNoFlags());
        s.setSpotCheckPercent(r.spotCheckPercent());
        s.setGreenMinRating(r.greenMinRating());
        s.setAmberMinRating(r.amberMinRating());
        s.setNeedsMeDaysWithoutSession(r.needsMeDaysWithoutSession());
        s.setWeeklyReviewDay(r.weeklyReviewDay());
        s.setMonthlySpendLimitUsd(r.monthlySpendLimitUsd());
        AiSettings saved = repository.save(s);
        log.info("Updated AI settings (enabled={}, practice={}, reflections={}, mode={})",
                saved.isAiEnabled(), saved.getPracticeProvider(), saved.getReflectionProvider(), saved.getCallMode());
        return toResponse(saved);
    }

    /** Records that tonight's run has started, so it is not started twice. */
    @Transactional
    public void markRunDate(LocalDate date) {
        AiSettings s = getActive();
        s.setLastRunDate(date);
        repository.save(s);
    }

    public AiSettingsResponse toResponse(AiSettings s) {
        return new AiSettingsResponse(
                s.isAiEnabled(),
                s.getPracticeProvider(),
                s.getReflectionProvider(),
                s.getClaudeModel(),
                s.getGeminiModel(),
                s.getGeminiTranscribeModel(),
                s.getClaudeEffort(),
                s.getCallMode(),
                s.isBackupEnabled(),
                s.getNightlyRunTime(),
                s.getRunTimezone(),
                s.isAutoApproveEnabled(),
                s.getAutoApproveMinRating(),
                s.isAutoApproveRequireNoFlags(),
                s.getSpotCheckPercent(),
                s.getGreenMinRating(),
                s.getAmberMinRating(),
                s.getNeedsMeDaysWithoutSession(),
                s.getWeeklyReviewDay(),
                s.getMonthlySpendLimitUsd(),
                s.getLastRunDate(),
                s.getAnalyseFrom(),
                claude.isConfigured(),
                gemini.isConfigured());
    }
}
