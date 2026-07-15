package com.kbv.education.service.impl;

import com.kbv.education.dto.analytics.AdminAnalyticsResponse;
import com.kbv.education.dto.analytics.StudentTrendResponse;
import com.kbv.education.dto.analytics.TrendPointResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.DashboardMetric;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.TierRule;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.DashboardMetricKey;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.DashboardMetricRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.TierRuleRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.AnalyticsService;
import com.kbv.education.service.TierEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Computes the spec's analytics metrics from current {@code student_scores}
 * rows and caches them in {@code dashboard_metrics}, keyed by
 * {@link DashboardMetricKey}. "Active students" and "at risk" aren't defined
 * precisely by the spec; here active = has a current score in scope, and at
 * risk = currently in the worst-ranked tier (documented in the Phase 4 plan).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final CohortRepository cohortRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final DashboardMetricRepository dashboardMetricRepository;
    private final TierRuleRepository tierRuleRepository;
    private final StudyDayRepository studyDayRepository;
    private final TierEngineService tierEngineService;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void refresh(UUID cohortId) {
        Cohort cohort = cohortId != null
                ? cohortRepository.findByIdAndDeletedFalse(cohortId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Cohort", cohortId))
                : null;

        List<StudentScore> scores = cohortId != null
                ? studentScoreRepository.findByCohort_IdAndCurrentTrueAndDeletedFalse(cohortId)
                : studentScoreRepository.findByCurrentTrueAndDeletedFalse();

        List<TierRule> tierRules = tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
        int worstRank = tierRules.isEmpty() ? 0 : tierRules.get(tierRules.size() - 1).getTierRank();

        LocalDate today = LocalDate.now();
        LocalDate weekFrom = today.minusDays(6);
        LocalDate monthFrom = today.minusDays(29);

        Map<DashboardMetricKey, Long> tierCounts = new EnumMap<>(DashboardMetricKey.class);
        long atRisk = 0;
        long weekly = 0;
        long monthly = 0;

        for (StudentScore score : scores) {
            UUID studentId = score.getStudent().getId();
            String displayTier = tierEngineService.getDisplayTier(studentId);
            TierRule matched = tierRules.stream()
                    .filter(r -> r.getTierName().equals(displayTier))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                tierCounts.merge(rankToKey(matched.getTierRank()), 1L, Long::sum);
                if (matched.getTierRank() == worstRank) {
                    atRisk++;
                }
            }
            // Sourced from study_days (not the raw practice_sessions/reflection_entries tables):
            // this method runs inside ActivityServiceImpl.record()'s own REQUIRES_NEW transaction,
            // which can't see rows the caller's still-open outer transaction just inserted into
            // those tables - but study_days is written by record() itself, in this transaction.
            weekly += studyDayRepository.countByStudent_IdAndHasReflectionTrueAndStudyDateBetweenAndDeletedFalse(
                            studentId, weekFrom, today)
                    + studyDayRepository.countByStudent_IdAndHasPracticeTrueAndStudyDateBetweenAndDeletedFalse(
                            studentId, weekFrom, today);
            monthly += studyDayRepository.countByStudent_IdAndHasReflectionTrueAndStudyDateBetweenAndDeletedFalse(
                            studentId, monthFrom, today)
                    + studyDayRepository.countByStudent_IdAndHasPracticeTrueAndStudyDateBetweenAndDeletedFalse(
                            studentId, monthFrom, today);
        }

        upsert(cohort, DashboardMetricKey.AVG_COMPOSITE, average(scores, StudentScore::getCompositeScore));
        upsert(cohort, DashboardMetricKey.HIGHEST_SCORE, extreme(scores, StudentScore::getCompositeScore, true));
        upsert(cohort, DashboardMetricKey.LOWEST_SCORE, extreme(scores, StudentScore::getCompositeScore, false));
        upsert(cohort, DashboardMetricKey.AVG_PRACTICE, average(scores, StudentScore::getPracticePercentage));
        upsert(cohort, DashboardMetricKey.AVG_REFLECTION, average(scores, StudentScore::getReflectionPercentage));
        upsert(cohort, DashboardMetricKey.AVG_HOMEWORK, average(scores, StudentScore::getHomeworkPercentage));
        upsert(cohort, DashboardMetricKey.AVG_QUIZ, average(scores, StudentScore::getQuizPercentage));
        upsert(cohort, DashboardMetricKey.ACTIVE_STUDENTS, BigDecimal.valueOf(scores.size()));
        upsert(cohort, DashboardMetricKey.AT_RISK_STUDENTS, BigDecimal.valueOf(atRisk));
        upsert(cohort, DashboardMetricKey.WEEKLY_ACTIVITY, BigDecimal.valueOf(weekly));
        upsert(cohort, DashboardMetricKey.MONTHLY_ACTIVITY, BigDecimal.valueOf(monthly));
        upsert(cohort, DashboardMetricKey.TIER_1_COUNT, BigDecimal.valueOf(tierCounts.getOrDefault(DashboardMetricKey.TIER_1_COUNT, 0L)));
        upsert(cohort, DashboardMetricKey.TIER_2_COUNT, BigDecimal.valueOf(tierCounts.getOrDefault(DashboardMetricKey.TIER_2_COUNT, 0L)));
        upsert(cohort, DashboardMetricKey.TIER_3_COUNT, BigDecimal.valueOf(tierCounts.getOrDefault(DashboardMetricKey.TIER_3_COUNT, 0L)));
        upsert(cohort, DashboardMetricKey.NOT_PASSING_COUNT,
                BigDecimal.valueOf(tierCounts.getOrDefault(DashboardMetricKey.NOT_PASSING_COUNT, 0L)));

        log.info("Refreshed analytics for {} ({} students)", cohortId == null ? "global" : "cohort " + cohortId, scores.size());
    }

    @Override
    @Transactional
    public AdminAnalyticsResponse get(UUID cohortId) {
        List<DashboardMetric> metrics = loadMetrics(cohortId);
        if (metrics.isEmpty()) {
            refresh(cohortId);
            metrics = loadMetrics(cohortId);
        }

        Map<DashboardMetricKey, BigDecimal> byKey = new EnumMap<>(DashboardMetricKey.class);
        for (DashboardMetric metric : metrics) {
            byKey.put(metric.getMetricKey(), metric.getMetricValue());
        }

        List<TierRule> tierRules = tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
        Map<String, Long> tierDistribution = new LinkedHashMap<>();
        for (TierRule rule : tierRules) {
            long count = byKey.getOrDefault(rankToKey(rule.getTierRank()), BigDecimal.ZERO).longValue();
            tierDistribution.put(rule.getTierName(), count);
        }

        Instant computedAt = metrics.stream()
                .map(DashboardMetric::getComputedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());

        return new AdminAnalyticsResponse(
                byKey.getOrDefault(DashboardMetricKey.AVG_COMPOSITE, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.HIGHEST_SCORE, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.LOWEST_SCORE, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.AVG_PRACTICE, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.AVG_REFLECTION, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.AVG_HOMEWORK, BigDecimal.ZERO),
                byKey.getOrDefault(DashboardMetricKey.AVG_QUIZ, BigDecimal.ZERO),
                tierDistribution,
                byKey.getOrDefault(DashboardMetricKey.ACTIVE_STUDENTS, BigDecimal.ZERO).longValue(),
                byKey.getOrDefault(DashboardMetricKey.AT_RISK_STUDENTS, BigDecimal.ZERO).longValue(),
                byKey.getOrDefault(DashboardMetricKey.WEEKLY_ACTIVITY, BigDecimal.ZERO).longValue(),
                byKey.getOrDefault(DashboardMetricKey.MONTHLY_ACTIVITY, BigDecimal.ZERO).longValue(),
                computedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrendPointResponse> trend(UUID cohortId, LeaderboardSortField metric, int days) {
        Instant from = windowStart(days);
        List<StudentScore> scores = cohortId != null
                ? studentScoreRepository.findByCohort_IdAndCreatedAtAfterAndDeletedFalse(cohortId, from)
                : studentScoreRepository.findByCreatedAtAfterAndDeletedFalse(from);

        // Bucketed by the day each contributing recalculation happened, not a point-in-time
        // reconstruction for every calendar day - see the class javadoc's WEEKLY/MONTHLY note
        // for why: simpler, and still a genuinely meaningful trend of active-student activity.
        Map<LocalDate, List<BigDecimal>> byDate = new TreeMap<>();
        for (StudentScore score : scores) {
            LocalDate date = score.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDate.computeIfAbsent(date, d -> new ArrayList<>()).add(valueFor(score, metric));
        }

        return byDate.entrySet().stream()
                .map(e -> new TrendPointResponse(e.getKey(), averageValues(e.getValue())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentTrendResponse> studentTrend(List<UUID> studentIds, int days) {
        Instant from = windowStart(days);
        List<StudentTrendResponse> result = new ArrayList<>();
        for (UUID studentId : studentIds) {
            User student = userRepository.findByIdAndDeletedFalse(studentId).orElse(null);
            if (student == null) {
                continue;
            }
            List<TrendPointResponse> points = studentScoreRepository
                    .findByStudent_IdAndCreatedAtAfterAndDeletedFalseOrderByCreatedAtAsc(studentId, from).stream()
                    .map(s -> new TrendPointResponse(
                            s.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), s.getCompositeScore()))
                    .toList();
            result.add(new StudentTrendResponse(studentId, student.getFullName(), points));
        }
        return result;
    }

    // --- helpers -------------------------------------------------------

    private List<DashboardMetric> loadMetrics(UUID cohortId) {
        return cohortId != null
                ? dashboardMetricRepository.findByCohort_IdAndDeletedFalse(cohortId)
                : dashboardMetricRepository.findByCohortIsNullAndDeletedFalse();
    }

    /** Rank 1/2/3 map to their named slot; anything else (i.e. the worst rank) is "Not Passing". */
    private DashboardMetricKey rankToKey(int rank) {
        return switch (rank) {
            case 1 -> DashboardMetricKey.TIER_1_COUNT;
            case 2 -> DashboardMetricKey.TIER_2_COUNT;
            case 3 -> DashboardMetricKey.TIER_3_COUNT;
            default -> DashboardMetricKey.NOT_PASSING_COUNT;
        };
    }

    private BigDecimal average(List<StudentScore> scores, Function<StudentScore, BigDecimal> extractor) {
        return averageValues(scores.stream().map(extractor).toList());
    }

    private BigDecimal averageValues(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal valueFor(StudentScore score, LeaderboardSortField metric) {
        return switch (metric) {
            case COMPOSITE -> score.getCompositeScore();
            case PRACTICE -> score.getPracticePercentage();
            case QUIZ -> score.getQuizPercentage();
            case REFLECTION -> score.getReflectionPercentage();
            case HOMEWORK -> score.getHomeworkPercentage();
        };
    }

    private Instant windowStart(int days) {
        return LocalDate.now().minusDays(Math.max(days, 1) - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal extreme(List<StudentScore> scores, Function<StudentScore, BigDecimal> extractor, boolean max) {
        Optional<BigDecimal> result = scores.stream().map(extractor)
                .reduce(max ? BigDecimal::max : BigDecimal::min);
        return result.orElse(BigDecimal.ZERO);
    }

    private void upsert(Cohort cohort, DashboardMetricKey key, BigDecimal value) {
        Optional<DashboardMetric> existing = cohort != null
                ? dashboardMetricRepository.findByCohort_IdAndMetricKeyAndDeletedFalse(cohort.getId(), key)
                : dashboardMetricRepository.findByCohortIsNullAndMetricKeyAndDeletedFalse(key);
        DashboardMetric metric = existing.orElseGet(DashboardMetric::new);
        metric.setCohort(cohort);
        metric.setMetricKey(key);
        metric.setMetricValue(value.setScale(2, RoundingMode.HALF_UP));
        metric.setComputedAt(Instant.now());
        dashboardMetricRepository.save(metric);
    }
}
