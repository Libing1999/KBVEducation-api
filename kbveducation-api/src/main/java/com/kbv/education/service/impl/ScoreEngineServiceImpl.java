package com.kbv.education.service.impl;

import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.dto.tier.CurrentTierResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.QuizAttempt;
import com.kbv.education.entity.ScoreConfig;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.ScoreTriggerReason;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.ScoreConfigRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.LeaderboardService;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Implements the composite-score formula exactly as specified: weighted sum
 * of Practice/Reflection/Homework/Quiz percentages, using the active
 * {@link ScoreConfig}. Every recalculation is appended to {@code student_scores}
 * rather than updated in place, so the table doubles as the score history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreEngineServiceImpl implements ScoreEngineService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final ScoreConfigRepository scoreConfigRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final StudyDayRepository studyDayRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final TierEngineService tierEngineService;
    private final LeaderboardService leaderboardService;

    @Override
    @Transactional
    public StudentScoreResponse recalculate(UUID studentId, ScoreTriggerReason reason) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        ScoreConfig config = activeConfig();
        Cohort cohort = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort())
                .orElse(null);

        BigDecimal practice = practicePercentage(studentId, config, cohort);
        BigDecimal reflection = reflectionPercentage(studentId, config);
        BigDecimal homework = homeworkPercentage(studentId, config);
        BigDecimal quiz = quizPercentage(studentId);
        BigDecimal composite = composite(practice, reflection, homework, quiz, config);

        studentScoreRepository.clearCurrent(studentId);

        StudentScore score = new StudentScore();
        score.setStudent(student);
        score.setCohort(cohort);
        score.setPracticePercentage(practice);
        score.setReflectionPercentage(reflection);
        score.setHomeworkPercentage(homework);
        score.setQuizPercentage(quiz);
        score.setCompositeScore(composite);
        score.setPracticeWeight(config.getPracticeWeight());
        score.setReflectionWeight(config.getReflectionWeight());
        score.setHomeworkWeight(config.getHomeworkWeight());
        score.setQuizWeight(config.getQuizWeight());
        score.setTriggerReason(reason);
        score.setCurrent(true);
        StudentScore saved = studentScoreRepository.save(score);

        tierEngineService.recalculateCalculatedTier(studentId);

        log.info("Recalculated score for student {} ({}): composite={}", studentId, reason, composite);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void recalculateForCohort(UUID cohortId, ScoreTriggerReason reason) {
        studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(cohortId)
                .forEach(sc -> recalculate(sc.getStudent().getId(), reason));
        // Regenerated once for the whole cohort here, not inside recalculate() itself, so a
        // cohort-wide recalc (e.g. a config change) doesn't rebuild the leaderboard N times.
        leaderboardService.regenerate(cohortId);
    }

    @Override
    @Transactional
    public void recalculateAll(ScoreTriggerReason reason) {
        cohortRepository.findByStatusAndDeletedFalse(CohortStatus.ACTIVE)
                .forEach(cohort -> recalculateForCohort(cohort.getId(), reason));
    }

    @Override
    @Transactional
    public StudentScoreResponse getCurrent(UUID studentId) {
        return studentScoreRepository.findByStudent_IdAndCurrentTrueAndDeletedFalse(studentId)
                .map(this::toResponse)
                .orElseGet(() -> recalculate(studentId, ScoreTriggerReason.MANUAL_RECALC));
    }

    @Override
    @Transactional(readOnly = true)
    public PaceProjection getPaceProjection(UUID studentId) {
        StudentScoreResponse current = getCurrent(studentId);
        ScoreConfig config = activeConfig();
        Cohort cohort = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort())
                .orElse(null);

        BigDecimal homework = current.homeworkPercentage();
        BigDecimal quiz = current.quizPercentage();
        BigDecimal atRecentPace = compositeForTrailingWindow(studentId, config, cohort, 7, homework, quiz);
        BigDecimal last3Days = compositeForTrailingWindow(studentId, config, cohort, 3, homework, quiz);

        CurrentTierResponse tier = tierEngineService.getCurrentTier(studentId);
        Double nextTierThreshold = tier.remainingRequirements().stream()
                .filter(r -> r.metric().toLowerCase().contains("composite"))
                .map(CurrentTierResponse.RemainingRequirement::required)
                .findFirst()
                .orElse(null);

        return new PaceProjection(current.compositeScore().doubleValue(), atRecentPace.doubleValue(),
                last3Days.doubleValue(), nextTierThreshold, tier.nextPossibleTier());
    }

    /** Same composite() formula as {@link #recalculate}, but Practice/Reflection use only the
     * trailing {@code days} days instead of the full configured window — homework/quiz aren't
     * day-windowed so they're held at their current values. */
    private BigDecimal compositeForTrailingWindow(UUID studentId, ScoreConfig config, Cohort cohort,
                                                    int days, BigDecimal homework, BigDecimal quiz) {
        LocalDate today = LocalDate.now();
        LocalDate end = cohort != null && today.isAfter(cohort.getEndDate()) ? cohort.getEndDate() : today;
        LocalDate start = end.minusDays(days - 1L);

        BigDecimal practice = cohort == null ? BigDecimal.ZERO : percentageForWindow(studentId, start, end, true);
        BigDecimal reflection = percentageForWindow(studentId, start, end, false);
        return composite(practice, reflection, homework, quiz, config);
    }

    /** Shared by the configured-window Practice/Reflection formulas and the trailing-window pace
     * projection: study-days with the relevant flag / voided-excluded available days, over [start,end]. */
    private BigDecimal percentageForWindow(UUID studentId, LocalDate start, LocalDate end, boolean practice) {
        if (end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long voided = studyDayRepository.countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
                studentId, start, end);
        long available = Math.max(0, totalDays - voided);
        long activeDays = practice
                ? studyDayRepository.countByStudent_IdAndHasPracticeTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                        studentId, start, end)
                : studyDayRepository.countByStudent_IdAndHasReflectionTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                        studentId, start, end);
        return percentOf(activeDays, available);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentScoreResponse> getHistory(UUID studentId, int page, int size) {
        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", List.of("createdAt"));
        Page<StudentScore> result =
                studentScoreRepository.findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    // ========================= formulas =========================

    /** Study Days / Available Practice Days x 100, over [practiceWindowStart, min(today, cohort.endDate)]. */
    private BigDecimal practicePercentage(UUID studentId, ScoreConfig config, Cohort cohort) {
        LocalDate start = config.getPracticeWindowStart();
        if (start == null || cohort == null) {
            return BigDecimal.ZERO;
        }
        LocalDate today = LocalDate.now();
        LocalDate end = today.isBefore(cohort.getEndDate()) ? today : cohort.getEndDate();
        if (end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        long totalDays = ChronoUnit.DAYS.between(start, end) + 1;
        long voided = studyDayRepository.countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
                studentId, start, end);
        long available = Math.max(0, totalDays - voided);
        long studyDays = studyDayRepository
                .countByStudent_IdAndHasPracticeTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                        studentId, start, end);
        return percentOf(studyDays, available);
    }

    /** Reflection Days / Total Reflection Days x 100, where eligible days fall in the reflection window. */
    private BigDecimal reflectionPercentage(UUID studentId, ScoreConfig config) {
        LocalDate start = config.getReflectionWindowStart();
        LocalDate end = config.getReflectionWindowEnd();
        long available = config.getTotalReflectionDays();
        if (start == null || end == null || end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        long voided = studyDayRepository.countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
                studentId, start, end);
        available = Math.max(0, available - voided);
        long reflectionDays = studyDayRepository
                .countByStudent_IdAndHasReflectionTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                        studentId, start, end);
        return percentOf(reflectionDays, available);
    }

    /** Submitted Homework / Total Homework x 100. */
    private BigDecimal homeworkPercentage(UUID studentId, ScoreConfig config) {
        long submitted = homeworkSubmissionRepository.countByStudent_IdAndDeletedFalse(studentId);
        return percentOf(submitted, config.getTotalHomeworkCount());
    }

    /** Average score of completed (SUBMITTED) quizzes; incomplete attempts are ignored. */
    private BigDecimal quizPercentage(UUID studentId) {
        List<QuizAttempt> attempts =
                quizAttemptRepository.findByStudent_IdAndStatusAndDeletedFalse(studentId, AttemptStatus.SUBMITTED);
        BigDecimal sum = BigDecimal.ZERO;
        int counted = 0;
        for (QuizAttempt attempt : attempts) {
            Integer maxScore = attempt.getMaxScore();
            if (maxScore == null || maxScore == 0) {
                continue;
            }
            int score = attempt.getScore() == null ? 0 : attempt.getScore();
            sum = sum.add(BigDecimal.valueOf(score)
                    .multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(maxScore), 4, RoundingMode.HALF_UP));
            counted++;
        }
        if (counted == 0) {
            return BigDecimal.ZERO;
        }
        return clamp(sum.divide(BigDecimal.valueOf(counted), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal composite(BigDecimal practice, BigDecimal reflection, BigDecimal homework, BigDecimal quiz,
                                  ScoreConfig config) {
        BigDecimal weighted = practice.multiply(config.getPracticeWeight())
                .add(reflection.multiply(config.getReflectionWeight()))
                .add(homework.multiply(config.getHomeworkWeight()))
                .add(quiz.multiply(config.getQuizWeight()))
                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return clamp(weighted.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal percentOf(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal pct = BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        return clamp(pct);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (value.compareTo(HUNDRED) > 0) {
            return HUNDRED.setScale(2, RoundingMode.HALF_UP);
        }
        return value;
    }

    private ScoreConfig activeConfig() {
        return scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active score configuration found"));
    }

    private StudentScoreResponse toResponse(StudentScore score) {
        return new StudentScoreResponse(
                score.getId(),
                score.getStudent().getId(),
                score.getPracticePercentage(),
                score.getReflectionPercentage(),
                score.getHomeworkPercentage(),
                score.getQuizPercentage(),
                score.getCompositeScore(),
                score.getCreatedAt());
    }
}
