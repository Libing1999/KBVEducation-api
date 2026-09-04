package com.kbv.education.service.impl;

import com.kbv.education.dto.certificate.CertificateResponse;
import com.kbv.education.dto.parent.ParentChildResponse;
import com.kbv.education.dto.parent.ParentSummaryResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.Homework;
import com.kbv.education.entity.ScoreConfig;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.repository.ScoreConfigRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.CertificateService;
import com.kbv.education.service.ParentSummaryService;
import com.kbv.education.service.ProgressService;
import com.kbv.education.service.TierEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds the single Parent screen's weekly summary. Reuses the same
 * repositories/services {@link ProgressServiceImpl}, {@link ScoreEngineServiceImpl},
 * and {@link DashboardServiceImpl} already use for parent-linked-student
 * resolution and activity counts, rather than re-deriving any of it.
 *
 * <p>Two thresholds below are judgment calls not pinned down anywhere in the
 * design brief (see the class-level constants): the "final weeks" tier-line
 * cutoff, and what counts as the cohort's "just started" first week.</p>
 */
@Service
@RequiredArgsConstructor
public class ParentSummaryServiceImpl implements ParentSummaryService {

    /**
     * Fixed 24h "urgent" cutoff for the action strip. Per the Parent UI design's
     * LIVE-AND-ADMIN-NOTES.md this is flagged as something admins may want to
     * configure later; no such setting exists anywhere in the app yet, so — like
     * the weekly cadence text below — it stays a hardcoded constant for now.
     */
    private static final long URGENT_CUTOFF_HOURS = 24;

    /**
     * The tier line shows only "in the final 2-3 weeks" per NOTES.md, which doesn't
     * pin down an exact number. Picked the top of that range: within 21 days of the
     * cohort's exam date (falling back to its end date when no exam date is set).
     */
    private static final long TIER_LINE_WINDOW_DAYS = 21;

    /**
     * During the cohort's first calendar week, "this week" counts aren't
     * meaningful yet (see NOTES.md's week1 state: "never shown as 0 of 0") — the
     * frontend shows a plain "just started" message instead.
     */
    private static final long FIRST_WEEK_DAYS = 7;

    /**
     * Hardcoded. Per LIVE-AND-ADMIN-NOTES.md, "Updates every Monday" should
     * ultimately be an admin setting for which day a weekly batch recomputes —
     * no such batch exists; this summary is instead computed live on every
     * request, so it can never drift out of sync with what it displays.
     */
    private static final String CADENCE_TEXT = "Updates every Monday.";

    private final ProgressService progressService;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final StudyDayRepository studyDayRepository;
    private final ScoreConfigRepository scoreConfigRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizRepository quizRepository;
    private final TierEngineService tierEngineService;
    private final CertificateService certificateService;
    private final ParentStudentRepository parentStudentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ParentChildResponse> listMyChildren(UUID parentUserId) {
        return parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(parentUserId).stream()
                .map(link -> new ParentChildResponse(
                        link.getStudent().getId(), link.getStudent().getFirstName(), link.getStudent().getLastName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ParentSummaryResponse getSummary(UUID parentUserId, UUID requestedStudentId) {
        UUID studentId = progressService.resolveStudentId(parentUserId, requestedStudentId);
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        StudentCohort membership = studentCohortRepository
                .findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Your linked student is not currently enrolled in a cohort"));
        Cohort cohort = membership.getCohort();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        boolean justStarted = today.isBefore(cohort.getStartDate().plusDays(FIRST_WEEK_DAYS));

        ParentSummaryResponse.WeekMetric practice = null;
        ParentSummaryResponse.WeekMetric reflection = null;
        ParentSummaryResponse.ActionItem action = null;
        if (!justStarted) {
            practice = practiceWeekMetric(studentId, cohort, weekStart, weekEnd);
            reflection = reflectionWeekMetric(studentId, cohort, weekStart, weekEnd);
            action = nextDueAction(studentId, cohort.getId());
        }

        ParentSummaryResponse.CompletionCount quizzes = quizCompletionCount(studentId, cohort.getId());
        ParentSummaryResponse.CompletionCount homework = homeworkCompletionCount(studentId);
        ParentSummaryResponse.CertificateInfo certificate = latestCertificate(parentUserId, studentId);

        String tierLine = (certificate != null || isFinalWeeks(cohort, today))
                ? tierEngineService.getDisplayTier(studentId)
                : null;

        return new ParentSummaryResponse(
                student.getFullName(),
                cohort.getName(),
                weekRangeLabel(weekStart, weekEnd),
                action,
                justStarted,
                practice,
                reflection,
                quizzes,
                homework,
                certificate,
                tierLine,
                CADENCE_TEXT);
    }

    // ========================= "This week" / "Course so far" =========================

    /**
     * Denominator/numerator for the current Mon-Sun week, clipped to the cohort's active
     * dates and reduced by any admin-voided (excused) days — the same voided-day concept
     * {@link ScoreEngineServiceImpl} uses, reused rather than reinvented. Deliberately NOT
     * clipped to "days elapsed so far" — the week's target denominator is the whole week,
     * same as the reference design's always-out-of-7 framing (minus excused days).
     */
    private ParentSummaryResponse.WeekMetric practiceWeekMetric(UUID studentId, Cohort cohort,
                                                                  LocalDate weekStart, LocalDate weekEnd) {
        LocalDate from = maxDate(weekStart, cohort.getStartDate());
        LocalDate to = minDate(weekEnd, cohort.getEndDate());
        long weekTotal = availableDays(studentId, from, to);
        long weekDone = practiceDoneDays(studentId, from, to);

        ScoreConfig config = activeScoreConfig();
        LocalDate courseFrom = config.getPracticeWindowStart();
        long courseTotal = 0;
        long courseDone = 0;
        if (courseFrom != null) {
            LocalDate courseTo = minDate(LocalDate.now(), cohort.getEndDate());
            courseTotal = availableDays(studentId, courseFrom, courseTo);
            courseDone = practiceDoneDays(studentId, courseFrom, courseTo);
        }
        return new ParentSummaryResponse.WeekMetric(
                (int) weekDone, (int) weekTotal, (int) courseDone, (int) courseTotal);
    }

    private ParentSummaryResponse.WeekMetric reflectionWeekMetric(UUID studentId, Cohort cohort,
                                                                    LocalDate weekStart, LocalDate weekEnd) {
        LocalDate from = maxDate(weekStart, cohort.getStartDate());
        LocalDate to = minDate(weekEnd, cohort.getEndDate());
        long weekTotal = availableDays(studentId, from, to);
        long weekDone = reflectionDoneDays(studentId, from, to);

        // Course-so-far mirrors ScoreEngineServiceImpl#reflectionPercentage exactly: a fixed
        // admin-set total (ScoreConfig.totalReflectionDays), not a date-span computation.
        ScoreConfig config = activeScoreConfig();
        LocalDate courseFrom = config.getReflectionWindowStart();
        LocalDate courseTo = config.getReflectionWindowEnd();
        long courseTotal = 0;
        long courseDone = 0;
        if (courseFrom != null && courseTo != null && !courseTo.isBefore(courseFrom)) {
            long voided = studyDayRepository.countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
                    studentId, courseFrom, courseTo);
            courseTotal = Math.max(0, config.getTotalReflectionDays() - voided);
            courseDone = reflectionDoneDays(studentId, courseFrom, courseTo);
        }
        return new ParentSummaryResponse.WeekMetric(
                (int) weekDone, (int) weekTotal, (int) courseDone, (int) courseTotal);
    }

    private long availableDays(UUID studentId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        long voided = studyDayRepository.countByStudent_IdAndVoidedTrueAndStudyDateBetweenAndDeletedFalse(
                studentId, from, to);
        return Math.max(0, totalDays - voided);
    }

    private long practiceDoneDays(UUID studentId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return studyDayRepository.countByStudent_IdAndHasPracticeTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                studentId, from, to);
    }

    private long reflectionDoneDays(UUID studentId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return 0;
        }
        return studyDayRepository.countByStudent_IdAndHasReflectionTrueAndVoidedFalseAndStudyDateBetweenAndDeletedFalse(
                studentId, from, to);
    }

    // ========================= completion counts =========================

    private ParentSummaryResponse.CompletionCount homeworkCompletionCount(UUID studentId) {
        long total = activeScoreConfig().getTotalHomeworkCount();
        long done = homeworkSubmissionRepository.countByStudent_IdAndDeletedFalse(studentId);
        return new ParentSummaryResponse.CompletionCount((int) done, (int) total);
    }

    /**
     * Quizzes have no admin-set "total" analogous to {@code ScoreConfig.totalHomeworkCount},
     * so the denominator here is a best-effort live count of published quizzes across the
     * student's cohort lessons instead of an admin threshold.
     */
    private ParentSummaryResponse.CompletionCount quizCompletionCount(UUID studentId, UUID cohortId) {
        long total = quizRepository.countPublishedByCohortId(cohortId);
        long done = quizAttemptRepository.countByStudent_IdAndStatusAndDeletedFalse(studentId, AttemptStatus.SUBMITTED);
        return new ParentSummaryResponse.CompletionCount((int) done, (int) total);
    }

    // ========================= action strip =========================

    /**
     * Best-effort "next thing due": nearest upcoming {@link Homework} due date across the
     * student's cohort that they haven't submitted yet. Quiz has no due-date field in the
     * data model at all (only Homework does), so this can't also consider quizzes — flagged
     * in the implementation report.
     */
    private ParentSummaryResponse.ActionItem nextDueAction(UUID studentId, UUID cohortId) {
        Instant now = Instant.now();
        List<Homework> upcoming = homeworkRepository.findUpcomingByCohortId(cohortId, now);
        for (Homework hw : upcoming) {
            if (homeworkSubmissionRepository.existsByHomework_IdAndStudent_IdAndDeletedFalse(hw.getId(), studentId)) {
                continue;
            }
            Instant due = hw.getDueDate();
            long hoursLeft = Duration.between(now, due).toHours();
            long daysLeft = Math.max(1, (long) Math.ceil(hoursLeft / 24.0));
            String dayName = due.atZone(ZoneOffset.UTC).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            String label = hw.getTitle() + " due " + dayName;
            String daysLeftLabel = daysLeft == 1 ? "1 day left" : daysLeft + " days left";
            boolean urgent = hoursLeft <= URGENT_CUTOFF_HOURS;
            return new ParentSummaryResponse.ActionItem(label, daysLeftLabel, urgent);
        }
        return null;
    }

    // ========================= certificate / tier =========================

    private ParentSummaryResponse.CertificateInfo latestCertificate(UUID parentUserId, UUID studentId) {
        List<CertificateResponse> certificates = certificateService.listForParent(parentUserId, studentId);
        if (certificates.isEmpty()) {
            return null;
        }
        CertificateResponse latest = certificates.get(0); // ordered by createdAt desc
        String tierLabel = latest.tierAtIssue() == null ? "" : latest.tierAtIssue();
        if (latest.cohortName() != null && !latest.cohortName().isBlank()) {
            tierLabel = tierLabel.isBlank() ? latest.cohortName() : tierLabel + " — " + latest.cohortName();
        }
        return new ParentSummaryResponse.CertificateInfo(latest.id(), latest.certificateNumber(), tierLabel);
    }

    private boolean isFinalWeeks(Cohort cohort, LocalDate today) {
        LocalDate reference = cohort.getExamDate() != null ? cohort.getExamDate() : cohort.getEndDate();
        if (reference == null) {
            return false;
        }
        return !today.isBefore(reference.minusDays(TIER_LINE_WINDOW_DAYS));
    }

    // ========================= misc helpers =========================

    private ScoreConfig activeScoreConfig() {
        return scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .orElseThrow(() -> new ResourceNotFoundException("No active score configuration found"));
    }

    private String weekRangeLabel(LocalDate start, LocalDate end) {
        DateTimeFormatter monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        if (start.getMonth() == end.getMonth()) {
            return start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + " " + start.getDayOfMonth()
                    + "–" + end.getDayOfMonth();
        }
        return start.format(monthDay) + " – " + end.format(monthDay);
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
