package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.tier.CurrentTierResponse;
import com.kbv.education.dto.tier.TierHistoryResponse;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.TierHistory;
import com.kbv.education.entity.TierRule;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.entity.enums.ScoreTriggerReason;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.entity.enums.TierEventSource;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.repository.TierHistoryRepository;
import com.kbv.education.repository.TierRuleRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.utils.InputSanitizer;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Picks the best tier a student qualifies for by walking {@code tier_rules}
 * best-first and taking the first one whose min thresholds (composite,
 * practice %, full papers) are all met — falling through toward "Not
 * Passing" otherwise. {@code maxComposite} is informational only (keeps the
 * admin-facing bands non-overlapping); it isn't used as a gate, so a student
 * who clears a high composite but misses a lower tier's secondary gates
 * still lands in the best tier whose thresholds they actually satisfy,
 * rather than falling into a gap between bands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TierEngineServiceImpl implements TierEngineService {

    private final UserRepository userRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final TierRuleRepository tierRuleRepository;
    private final TierHistoryRepository tierHistoryRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final ScoreAuditLogService scoreAuditLogService;

    /** {@code @Lazy} field injection (not a Lombok constructor param) to break the circular
     * dependency: {@link ScoreEngineServiceImpl} already depends on {@link TierEngineService}
     * to compute a student's tier right after computing their score. */
    @Lazy
    @Autowired
    private ScoreEngineService scoreEngineService;

    @Override
    @Transactional
    public TierHistoryResponse recalculateCalculatedTier(UUID studentId) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        StudentScore score = currentScore(studentId);
        // Counts both legacy PAST_PAPER rows and the current past-paper study types.
        long fullPapers = practiceSessionRepository.countByStudent_IdAndStudyTypeInAndStatusAndDeletedFalse(
                studentId,
                Set.of(StudyType.PAST_PAPER, StudyType.PAST_PAPER_TEST_DAY, StudyType.PAST_PAPER_IMPROVEMENT_DAY),
                PracticeStatus.APPROVED);

        TierRule matched = matchTier(score.getCompositeScore(), score.getPracticePercentage(), fullPapers);

        TierHistory history = new TierHistory();
        history.setStudent(student);
        history.setCalculatedTier(matched.getTierName());
        history.setCompositeScore(score.getCompositeScore());
        history.setPracticePercentage(score.getPracticePercentage());
        history.setFullPapersCount((int) fullPapers);
        history.setSource(TierEventSource.SYSTEM);
        TierHistory saved = tierHistoryRepository.save(history);

        log.info("Recalculated tier for student {}: {}", studentId, matched.getTierName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CurrentTierResponse getCurrentTier(UUID studentId) {
        TierHistory latest = tierHistoryRepository.findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId)
                .orElseGet(() -> {
                    recalculateCalculatedTier(studentId);
                    return tierHistoryRepository.findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Tier calculation did not produce a history row for student " + studentId));
                });

        Optional<TierHistory> confirmedRow = tierHistoryRepository
                .findFirstByStudent_IdAndConfirmedTierIsNotNullAndDeletedFalseOrderByCreatedAtDesc(studentId);
        String confirmedTier = confirmedRow.map(TierHistory::getConfirmedTier).orElse(null);
        boolean isOverride = confirmedRow.map(TierHistory::isOverride).orElse(false);

        List<TierRule> rules = tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
        // "Next tier" is relative to the tier actually shown to the student (confirmed/overridden
        // if set, else calculated) - the same precedence used everywhere else (getDisplayTier()) -
        // not always the raw calculated tier, which would be wrong after an upward override.
        String displayTierName = confirmedTier != null ? confirmedTier : latest.getCalculatedTier();
        TierRule currentRule = rules.stream()
                .filter(r -> r.getTierName().equals(displayTierName))
                .findFirst()
                .orElse(null);
        TierRule nextRule = currentRule == null ? null : rules.stream()
                .filter(r -> r.getTierRank() == currentRule.getTierRank() - 1)
                .findFirst()
                .orElse(null);

        String nextTierName = null;
        List<CurrentTierResponse.RemainingRequirement> remaining = new ArrayList<>();
        if (nextRule != null) {
            nextTierName = nextRule.getTierName();
            if (latest.getCompositeScore().compareTo(nextRule.getMinComposite()) < 0) {
                remaining.add(new CurrentTierResponse.RemainingRequirement(
                        "Composite Score", latest.getCompositeScore().doubleValue(),
                        nextRule.getMinComposite().doubleValue()));
            }
            if (latest.getPracticePercentage().compareTo(nextRule.getMinPracticePercentage()) < 0) {
                remaining.add(new CurrentTierResponse.RemainingRequirement(
                        "Practice %", latest.getPracticePercentage().doubleValue(),
                        nextRule.getMinPracticePercentage().doubleValue()));
            }
            if (latest.getFullPapersCount() < nextRule.getMinFullPapers()) {
                remaining.add(new CurrentTierResponse.RemainingRequirement(
                        "Full Papers", latest.getFullPapersCount(), nextRule.getMinFullPapers()));
            }
        }

        return new CurrentTierResponse(latest.getCalculatedTier(), confirmedTier, isOverride, nextTierName, remaining);
    }

    @Override
    @Transactional
    public TierHistoryResponse confirm(UUID studentId, UUID adminId) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("Admin", adminId));
        TierHistory latest = tierHistoryRepository.findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No calculated tier exists for this student yet"));

        TierHistory confirmation = copyOf(latest, student);
        confirmation.setConfirmedTier(latest.getCalculatedTier());
        confirmation.setOverride(false);
        confirmation.setDecidedBy(admin);
        confirmation.setSource(TierEventSource.ADMIN_CONFIRM);
        TierHistory saved = tierHistoryRepository.save(confirmation);

        scoreAuditLogService.record(ScoreAuditEntityType.TIER, saved.getId(), studentId,
                "TIER_CONFIRMED", null, latest.getCalculatedTier(), null);

        log.info("Admin {} confirmed tier {} for student {}", adminId, latest.getCalculatedTier(), studentId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    @Audited(action = "TIER_OVERRIDDEN", entityType = "TIER")
    public TierHistoryResponse override(UUID studentId, String tierName, String reason, UUID adminId) {
        reason = InputSanitizer.sanitize(reason, 1000);
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("Admin", adminId));

        List<TierRule> rules = tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
        boolean validTier = rules.stream().anyMatch(r -> r.getTierName().equals(tierName));
        if (!validTier) {
            throw new BadRequestException("Unknown tier: " + tierName);
        }

        TierHistory latest = tierHistoryRepository.findFirstByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No calculated tier exists for this student yet"));

        TierHistory override = copyOf(latest, student);
        override.setConfirmedTier(tierName);
        override.setOverride(true);
        override.setOverrideReason(reason);
        override.setDecidedBy(admin);
        override.setSource(TierEventSource.ADMIN_OVERRIDE);
        TierHistory saved = tierHistoryRepository.save(override);

        scoreAuditLogService.record(ScoreAuditEntityType.TIER, saved.getId(), studentId,
                "TIER_OVERRIDDEN", latest.getCalculatedTier(), tierName, reason);

        log.info("Admin {} overrode tier to {} for student {} (reason: {})", adminId, tierName, studentId, reason);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TierHistoryResponse> history(UUID studentId, int page, int size) {
        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", List.of("createdAt"));
        Page<TierHistory> result =
                tierHistoryRepository.findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    // --- helpers -------------------------------------------------------

    private TierRule matchTier(BigDecimal composite, BigDecimal practice, long fullPapers) {
        List<TierRule> rules = tierRuleRepository.findByActiveTrueAndDeletedFalseOrderByTierRankAsc();
        if (rules.isEmpty()) {
            throw new ResourceNotFoundException("No tier rules are configured");
        }
        return rules.stream()
                .filter(r -> composite.compareTo(r.getMinComposite()) >= 0
                        && practice.compareTo(r.getMinPracticePercentage()) >= 0
                        && fullPapers >= r.getMinFullPapers())
                .findFirst()
                .orElse(rules.get(rules.size() - 1));
    }

    /** Carries forward the calculated-tier snapshot onto a new confirm/override decision row. */
    private TierHistory copyOf(TierHistory latest, User student) {
        TierHistory copy = new TierHistory();
        copy.setStudent(student);
        copy.setCalculatedTier(latest.getCalculatedTier());
        copy.setCompositeScore(latest.getCompositeScore());
        copy.setPracticePercentage(latest.getPracticePercentage());
        copy.setFullPapersCount(latest.getFullPapersCount());
        return copy;
    }

    /** Reads the student's current score, computing it first (same as {@link ScoreEngineService#getCurrent})
     * if this is the first time anything has ever needed it — e.g. a brand-new student's first page
     * load hitting the tier endpoint before anything else has triggered a score calculation. Previously
     * this threw when no score existed yet instead of computing one, a 404 a real student could hit on
     * their very first visit. */
    private StudentScore currentScore(UUID studentId) {
        return studentScoreRepository.findByStudent_IdAndCurrentTrueAndDeletedFalse(studentId)
                .orElseGet(() -> {
                    scoreEngineService.recalculate(studentId, ScoreTriggerReason.MANUAL_RECALC);
                    return studentScoreRepository.findByStudent_IdAndCurrentTrueAndDeletedFalse(studentId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "No score has been calculated for this student yet"));
                });
    }

    private TierHistoryResponse toResponse(TierHistory history) {
        return new TierHistoryResponse(
                history.getId(),
                history.getStudent().getId(),
                history.getCalculatedTier(),
                history.getConfirmedTier(),
                history.isOverride(),
                history.getOverrideReason(),
                history.getCompositeScore(),
                history.getPracticePercentage(),
                history.getFullPapersCount(),
                history.getDecidedBy() == null ? null : history.getDecidedBy().getFullName(),
                history.getSource(),
                history.getCreatedAt());
    }
}
