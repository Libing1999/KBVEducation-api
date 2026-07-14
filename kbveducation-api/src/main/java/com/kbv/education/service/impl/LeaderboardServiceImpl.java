package com.kbv.education.service.impl;

import com.kbv.education.dto.leaderboard.LeaderboardEntryResponse;
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

    @Override
    @Transactional
    public void regenerate(UUID cohortId) {
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
    @Transactional(readOnly = true)
    public List<LeaderboardEntryResponse> studentView(UUID studentId, LeaderboardSortField sortBy) {
        ScoreConfig config = activeConfig();
        if (!config.isLeaderboardEnabled()) {
            throw new BusinessRuleException("The leaderboard is currently disabled");
        }
        UUID cohortId = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getId())
                .orElseThrow(() -> new BusinessRuleException("You are not assigned to a cohort"));
        LeaderboardSortField effectiveSort = sortBy != null ? sortBy : config.getLeaderboardSortBy();
        return leaderboardSnapshotRepository
                .findByCohort_IdAndSortByAndDeletedFalseOrderByRankAsc(cohortId, effectiveSort).stream()
                .map(this::toResponse)
                .toList();
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
