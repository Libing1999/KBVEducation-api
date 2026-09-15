package com.kbv.education.service.impl;

import com.kbv.education.dto.leaderboard.LeaderboardEntryResponse;
import com.kbv.education.dto.leaderboard.LeaderboardStandingResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.LeaderboardSnapshot;
import com.kbv.education.entity.ScoreConfig;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.LeaderboardSnapshotRepository;
import com.kbv.education.repository.ScoreConfigRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.service.AnalyticsService;
import com.kbv.education.service.LeaderboardService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches leaderboard rankings per (cohort, sort field) instead of computing
 * them live on every read. Regenerated on-demand (score/tier-affecting
 * changes) and nightly as a safety net — see {@code LeaderboardRefreshScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final LeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private final ScoreConfigRepository scoreConfigRepository;
    private final TierEngineService tierEngineService;
    private final AnalyticsService analyticsService;

    @Override
    @Transactional
    public void regenerate(UUID cohortId) {
        // Analytics is a separate, independently-toggled feature (dashboardWidgetsEnabled, not
        // leaderboardEnabled), so it refreshes regardless of whether the leaderboard itself is on.
        analyticsService.refresh(cohortId);

        ScoreConfig config = activeConfig();
        if (!config.isLeaderboardEnabled()) {
            log.debug("Leaderboard disabled; skipping regeneration for cohort {}", cohortId);
            return;
        }
        Cohort cohort = cohortRepository.findByIdAndDeletedFalse(cohortId)
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", cohortId));
        List<StudentScore> scores = studentScoreRepository.findByCohort_IdAndCurrentTrueAndDeletedFalse(cohortId);

        for (LeaderboardSortField sortBy : LeaderboardSortField.values()) {
            // Hard-delete-then-insert: this is a disposable cache, not a history table, so the
            // bulk delete runs immediately and can't race the inserts below (unlike an
            // entity-level soft-delete, which Hibernate would otherwise flush after them).
            leaderboardSnapshotRepository.deleteByCohortAndSortBy(cohortId, sortBy);

            List<StudentScore> ranked = scores.stream()
                    .sorted(Comparator.comparing((StudentScore s) -> valueFor(s, sortBy)).reversed())
                    .toList();

            int rank = 1;
            for (StudentScore score : ranked) {
                LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
                snapshot.setCohort(cohort);
                snapshot.setStudent(score.getStudent());
                snapshot.setRank(rank++);
                snapshot.setCompositeScore(score.getCompositeScore());
                snapshot.setPracticePercentage(score.getPracticePercentage());
                snapshot.setReflectionPercentage(score.getReflectionPercentage());
                snapshot.setHomeworkPercentage(score.getHomeworkPercentage());
                snapshot.setQuizPercentage(score.getQuizPercentage());
                snapshot.setCurrentTier(tierEngineService.getDisplayTier(score.getStudent().getId()));
                snapshot.setSortBy(sortBy);
                snapshot.setGeneratedAt(Instant.now());
                leaderboardSnapshotRepository.save(snapshot);
            }
        }

        log.info("Regenerated leaderboard for cohort {} ({} students)", cohortId, scores.size());
    }

    @Override
    @Transactional
    public void regenerateForStudent(UUID studentId) {
        studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .ifPresent(sc -> regenerate(sc.getCohort().getId()));
    }

    @Override
    @Transactional
    public void regenerateAll() {
        cohortRepository.findByStatusAndDeletedFalse(CohortStatus.ACTIVE)
                .forEach(cohort -> regenerate(cohort.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaderboardEntryResponse> adminList(UUID cohortId, LeaderboardSortField sortBy,
                                                              int page, int size) {
        LeaderboardSortField effectiveSort = sortBy != null ? sortBy : activeConfig().getLeaderboardSortBy();
        Pageable pageable = PageableBuilder.build(page, size, "rank", "asc", List.of("rank"));
        Page<LeaderboardSnapshot> result = leaderboardSnapshotRepository
                .findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(cohortId, effectiveSort, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional
    public LeaderboardStandingResponse studentView(UUID studentId, LeaderboardSortField sortBy) {
        ScoreConfig config = activeConfig();
        if (!config.isLeaderboardEnabled()) {
            throw new BusinessRuleException("The leaderboard is currently disabled");
        }
        UUID cohortId = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getId())
                .orElseThrow(() -> new BusinessRuleException("You are not assigned to a cohort"));
        LeaderboardSortField effectiveSort = sortBy != null ? sortBy : config.getLeaderboardSortBy();

        List<LeaderboardSnapshot> ranked = leaderboardSnapshotRepository
                .findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(cohortId, effectiveSort);
        Optional<LeaderboardSnapshot> ownSnapshot = findOwn(ranked, studentId);

        // Self-heal instead of dead-ending: the snapshot is a cache, only rebuilt on specific
        // triggers (a submission, a config change, an explicit admin regenerate) — a student who
        // was just assigned to this cohort, or whose score was first calculated after the last
        // rebuild, has a real score but no row here yet. A student must always be able to see
        // their own position, so regenerate once and retry before concluding there's truly
        // nothing to show, rather than leaving them stuck behind a caching gap only an admin
        // action could otherwise clear.
        if (ownSnapshot.isEmpty()) {
            regenerate(cohortId);
            ranked = leaderboardSnapshotRepository
                    .findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(cohortId, effectiveSort);
            ownSnapshot = findOwn(ranked, studentId);
        }

        // Privacy boundary lives here, not in the controller or the frontend: the
        // response can never contain more than the public top-N plus the caller's
        // own row, regardless of what a client asks for.
        int topN = Math.max(1, config.getPublicTopN());
        List<LeaderboardEntryResponse> topEntries = ranked.stream().limit(topN).map(this::toResponse).toList();

        LeaderboardSnapshot own = ownSnapshot.orElseThrow(() -> new BusinessRuleException(
                "Your score hasn't been calculated yet — check back once your first activity is recorded"));

        return new LeaderboardStandingResponse(topEntries, toResponse(own), ranked.size(), topN);
    }

    private Optional<LeaderboardSnapshot> findOwn(List<LeaderboardSnapshot> ranked, UUID studentId) {
        return ranked.stream().filter(s -> s.getStudent().getId().equals(studentId)).findFirst();
    }

    private BigDecimal valueFor(StudentScore score, LeaderboardSortField sortBy) {
        return switch (sortBy) {
            case COMPOSITE -> score.getCompositeScore();
            case PRACTICE -> score.getPracticePercentage();
            case QUIZ -> score.getQuizPercentage();
            case REFLECTION -> score.getReflectionPercentage();
            case HOMEWORK -> score.getHomeworkPercentage();
        };
    }

    private ScoreConfig activeConfig() {
        return scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active score configuration found"));
    }

    private LeaderboardEntryResponse toResponse(LeaderboardSnapshot snapshot) {
        return new LeaderboardEntryResponse(
                snapshot.getRank(),
                snapshot.getStudent().getId(),
                snapshot.getStudent().getFullName(),
                snapshot.getCompositeScore(),
                snapshot.getCurrentTier(),
                snapshot.getPracticePercentage(),
                snapshot.getReflectionPercentage(),
                snapshot.getHomeworkPercentage(),
                snapshot.getQuizPercentage());
    }
}
