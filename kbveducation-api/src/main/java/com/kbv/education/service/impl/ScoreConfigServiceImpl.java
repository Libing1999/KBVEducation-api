package com.kbv.education.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.scoreconfig.ScoreConfigResponse;
import com.kbv.education.dto.scoreconfig.UpdateScoreConfigRequest;
import com.kbv.education.entity.ScoreConfig;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.entity.enums.ScoreTriggerReason;
import com.kbv.education.mapper.ScoreConfigMapper;
import com.kbv.education.repository.ScoreConfigRepository;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.service.ScoreConfigService;
import com.kbv.education.service.ScoreEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreConfigServiceImpl implements ScoreConfigService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final ScoreConfigRepository scoreConfigRepository;
    private final ScoreConfigMapper scoreConfigMapper;
    private final ScoreAuditLogService scoreAuditLogService;
    private final ScoreEngineService scoreEngineService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ScoreConfigResponse getActive() {
        return scoreConfigMapper.toResponse(getActiveConfig());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "scoreConfig", allEntries = true)
    public ScoreConfigResponse update(UpdateScoreConfigRequest request) {
        validateWeights(request);
        validateWindow(request.reflectionWindowStart(), request.reflectionWindowEnd());

        ScoreConfig config = getActiveConfig();
        String previousJson = toJson(scoreConfigMapper.toResponse(config));

        config.setPracticeWeight(request.practiceWeight());
        config.setReflectionWeight(request.reflectionWeight());
        config.setHomeworkWeight(request.homeworkWeight());
        config.setQuizWeight(request.quizWeight());
        config.setPracticeWindowStart(request.practiceWindowStart());
        config.setReflectionWindowStart(request.reflectionWindowStart());
        config.setReflectionWindowEnd(request.reflectionWindowEnd());
        config.setTotalReflectionDays(request.totalReflectionDays());
        config.setTotalHomeworkCount(request.totalHomeworkCount());
        config.setLeaderboardEnabled(request.leaderboardEnabled());
        config.setLeaderboardSortBy(request.leaderboardSortBy());
        config.setDashboardWidgetsEnabled(request.dashboardWidgetsEnabled());

        ScoreConfig saved = scoreConfigRepository.save(config);
        ScoreConfigResponse response = scoreConfigMapper.toResponse(saved);

        scoreAuditLogService.record(ScoreAuditEntityType.SCORE_CONFIG, saved.getId(), null,
                "WEIGHT_CHANGED", previousJson, toJson(response), null);

        scoreEngineService.recalculateAll(ScoreTriggerReason.CONFIG_CHANGE);

        log.info("Updated score config {}", saved.getId());
        return response;
    }

    private void validateWeights(UpdateScoreConfigRequest request) {
        BigDecimal sum = request.practiceWeight().add(request.reflectionWeight())
                .add(request.homeworkWeight()).add(request.quizWeight());
        if (sum.compareTo(HUNDRED) != 0) {
            throw new BusinessRuleException(
                    "Weights must total 100%%, but got %s%%".formatted(sum.stripTrailingZeros().toPlainString()));
        }
    }

    private void validateWindow(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("Reflection window end must be on or after the start date");
        }
    }

    private ScoreConfig getActiveConfig() {
        return scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active score configuration found"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
