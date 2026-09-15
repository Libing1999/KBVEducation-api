package com.kbv.education.service.impl;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.AdminDashboardTrendsResponse;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.entity.CohortDay;
import com.kbv.education.entity.ParentStudent;
import com.kbv.education.entity.StudentScore;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.entity.enums.CohortDayType;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.CohortMapper;
import com.kbv.education.mapper.UserMapper;
import com.kbv.education.repository.CohortDayRepository;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.StudentScoreRepository;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.entity.StudyDay;
import com.kbv.education.repository.UserSessionRepository;
import com.kbv.education.service.DashboardService;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.TierEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** Below this free space, the "System Health" dashboard card flags unhealthy (Phase 5 Step 7). */
    private static final long MIN_HEALTHY_FREE_DISK_MB = 500;

    private static final int MIN_TREND_DAYS = 7;
    private static final int MAX_TREND_DAYS = 90;
    private static final int TOP_STUDENTS_LIMIT = 5;

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final UserSessionRepository userSessionRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final ReflectionEntryRepository reflectionEntryRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final StudyDayRepository studyDayRepository;
    private final CohortDayRepository cohortDayRepository;
    private final com.kbv.education.repository.ScoreConfigRepository scoreConfigRepository;
    private final UserMapper userMapper;
    private final CohortMapper cohortMapper;
    private final ScoreEngineService scoreEngineService;
    private final TierEngineService tierEngineService;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard() {
        long totalStudents = userRepository.countByRole_NameAndDeletedFalse(RoleType.STUDENT);
        long totalParents = userRepository.countByRole_NameAndDeletedFalse(RoleType.PARENT);
        long totalCohorts = cohortRepository.countByDeletedFalse();
        long activeCohorts = cohortRepository.countByStatusAndDeletedFalse(CohortStatus.ACTIVE);
        long inactiveCohorts = totalCohorts - activeCohorts;

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        long todaysLogins = userSessionRepository.countByLoginAtAfter(startOfToday);

        List<UserResponse> recentUsers = userRepository.findTop5ByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(userMapper::toUserResponse)
                .toList();

        List<CohortResponse> recentCohorts = cohortRepository.findTop5ByDeletedFalseOrderByCreatedAtDesc().stream()
                .map(c -> cohortMapper.toResponse(c,
                        studentCohortRepository.countByCohort_IdAndActiveTrueAndDeletedFalse(c.getId())))
                .toList();

        long lockedAccounts = userRepository.countByLockedUntilAfterAndDeletedFalse(Instant.now());
        long freeDiskSpaceMb = new File(".").getFreeSpace() / (1024 * 1024);
        boolean systemHealthy = freeDiskSpaceMb >= MIN_HEALTHY_FREE_DISK_MB;

        return new AdminDashboardResponse(totalStudents, totalParents, totalCohorts,
                activeCohorts, inactiveCohorts, todaysLogins, lockedAccounts, systemHealthy, freeDiskSpaceMb,
                recentUsers, recentCohorts);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardTrendsResponse adminDashboardTrends(int days) {
        int windowDays = Math.min(Math.max(days, MIN_TREND_DAYS), MAX_TREND_DAYS);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate windowStart = today.minusDays(windowDays - 1L);

        List<AdminDashboardTrendsResponse.DailyValue> studentsGrowth = new ArrayList<>();
        List<AdminDashboardTrendsResponse.DailyValue> parentsGrowth = new ArrayList<>();
        List<AdminDashboardTrendsResponse.DailyValue> cohortsGrowth = new ArrayList<>();
        List<AdminDashboardTrendsResponse.DailyValue> activeCohortsGrowth = new ArrayList<>();
        List<AdminDashboardTrendsResponse.DailyValue> loginsPerDay = new ArrayList<>();
        List<AdminDashboardTrendsResponse.ActivityDay> activityTrend = new ArrayList<>();

        for (LocalDate day = windowStart; !day.isAfter(today); day = day.plusDays(1)) {
            Instant dayStart = day.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant dayEnd = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

            studentsGrowth.add(new AdminDashboardTrendsResponse.DailyValue(day,
                    userRepository.countByRole_NameAndCreatedAtBeforeAndDeletedFalse(RoleType.STUDENT, dayEnd)));
            parentsGrowth.add(new AdminDashboardTrendsResponse.DailyValue(day,
                    userRepository.countByRole_NameAndCreatedAtBeforeAndDeletedFalse(RoleType.PARENT, dayEnd)));
            cohortsGrowth.add(new AdminDashboardTrendsResponse.DailyValue(day,
                    cohortRepository.countByCreatedAtBeforeAndDeletedFalse(dayEnd)));
            activeCohortsGrowth.add(new AdminDashboardTrendsResponse.DailyValue(day,
                    cohortRepository.countByStatusAndCreatedAtBeforeAndDeletedFalse(CohortStatus.ACTIVE, dayEnd)));
            loginsPerDay.add(new AdminDashboardTrendsResponse.DailyValue(day,
                    userSessionRepository.countByLoginAtBetween(dayStart, dayEnd)));

            activityTrend.add(new AdminDashboardTrendsResponse.ActivityDay(
                    day,
                    reflectionEntryRepository.countByReflectionDateAndDeletedFalse(day),
                    practiceSessionRepository.countByStudyDateAndDeletedFalse(day),
                    homeworkSubmissionRepository.countBySubmittedAtBetweenAndDeletedFalse(dayStart, dayEnd),
                    quizAttemptRepository.countByStatusAndSubmittedAtBetweenAndDeletedFalse(
                            AttemptStatus.SUBMITTED, dayStart, dayEnd)));
        }

        Double studentsChangePct = percentChange(studentsGrowth);
        Double parentsChangePct = percentChange(parentsGrowth);
        Double cohortsChangePct = percentChange(cohortsGrowth);
        Double activeCohortsChangePct = percentChange(activeCohortsGrowth);

        long todaysLoginCount = loginsPerDay.isEmpty() ? 0 : loginsPerDay.get(loginsPerDay.size() - 1).value();
        long yesterdayLoginCount = loginsPerDay.size() < 2 ? 0 : loginsPerDay.get(loginsPerDay.size() - 2).value();
        Double loginsChangePct = percentChange(yesterdayLoginCount, todaysLoginCount);

        long activeCohortCount = cohortRepository.countByStatusAndDeletedFalse(CohortStatus.ACTIVE);
        long upcomingCohortCount = cohortRepository.countByStatusAndDeletedFalse(CohortStatus.UPCOMING);
        long totalCohortCount = cohortRepository.countByDeletedFalse();
        long inactiveCohortCount = totalCohortCount - activeCohortCount - upcomingCohortCount;
        AdminDashboardTrendsResponse.CohortStatusBreakdown cohortStatus =
                new AdminDashboardTrendsResponse.CohortStatusBreakdown(
                        activeCohortCount, inactiveCohortCount, upcomingCohortCount);

        List<AdminDashboardTrendsResponse.TopStudent> topStudents = studentScoreRepository
                .findByCurrentTrueAndDeletedFalse().stream()
                .sorted(Comparator.comparing(StudentScore::getCompositeScore).reversed())
                .limit(TOP_STUDENTS_LIMIT)
                .map(s -> new AdminDashboardTrendsResponse.TopStudent(
                        s.getStudent().getFullName(),
                        s.getCohort() != null ? s.getCohort().getName() : null,
                        s.getCompositeScore().doubleValue()))
                .toList();

        return new AdminDashboardTrendsResponse(
                studentsGrowth, parentsGrowth, cohortsGrowth, activeCohortsGrowth, loginsPerDay,
                studentsChangePct, parentsChangePct, cohortsChangePct, activeCohortsChangePct, loginsChangePct,
                activityTrend, cohortStatus, topStudents);
    }

    private Double percentChange(List<AdminDashboardTrendsResponse.DailyValue> series) {
        if (series.isEmpty()) {
            return null;
        }
        return percentChange(series.get(0).value(), series.get(series.size() - 1).value());
    }

    /** Null when the base is zero — a percentage vs. nothing isn't meaningful. */
    private Double percentChange(long from, long to) {
        if (from == 0) {
            return to == 0 ? 0.0 : null;
        }
        return ((to - from) / (double) from) * 100.0;
    }

    @Override
    @Transactional
    public ScoreDashboardResponse studentDashboard(UUID studentUserId) {
        User student = userRepository.findByIdAndDeletedFalse(studentUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentUserId));
        return buildScoreDashboard(student);
    }

    @Override
    @Transactional
    public ScoreDashboardResponse parentDashboard(UUID parentUserId, UUID requestedStudentId) {
        List<ParentStudent> links =
                parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(parentUserId);
        if (links.isEmpty()) {
            throw new BusinessRuleException("No student is linked to this parent account");
        }
        User student = requestedStudentId == null
                ? links.get(0).getStudent()
                : links.stream()
                        .map(ParentStudent::getStudent)
                        .filter(s -> s.getId().equals(requestedStudentId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("This student is not linked to your account"));
        return buildScoreDashboard(student);
    }

    private static final int ATTENDANCE_WINDOW_DAYS = 30;

    private ScoreDashboardResponse buildScoreDashboard(User student) {
        var membership = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId());
        ScoreDashboardResponse.CohortInfo cohortInfo = membership
                .map(sc -> new ScoreDashboardResponse.CohortInfo(
                        sc.getCohort().getName(), sc.getCohort().getStatus().name(), sc.getCohort().getExamDate()))
                .orElse(null);
        UUID cohortId = membership.map(sc -> sc.getCohort().getId()).orElse(null);

        StudentScoreResponse score = scoreEngineService.getCurrent(student.getId());
        String displayTier = tierEngineService.getDisplayTier(student.getId());

        ScoreEngineService.PaceProjection paceProjection = scoreEngineService.getPaceProjection(student.getId());
        ScoreDashboardResponse.PaceProjection pace = new ScoreDashboardResponse.PaceProjection(
                paceProjection.now(), paceProjection.atRecentPace(), paceProjection.last3Days(),
                paceProjection.nextTierThreshold(), paceProjection.nextTierName());

        List<ScoreDashboardResponse.AttendanceDay> attendance = buildAttendance(student.getId(), cohortId);

        ScoreDashboardResponse.Weights weights = scoreConfigRepository.findByActiveTrueAndDeletedFalse()
                .map(c -> new ScoreDashboardResponse.Weights(
                        c.getPracticeWeight().doubleValue(), c.getReflectionWeight().doubleValue(),
                        c.getHomeworkWeight().doubleValue(), c.getQuizWeight().doubleValue()))
                .orElse(new ScoreDashboardResponse.Weights(0, 0, 0, 0));

        List<ScoreDashboardResponse.LessonPlaceholder> lessons = List.of(
                new ScoreDashboardResponse.LessonPlaceholder("Module 3: Comprehension", "2026-07-15T10:00:00Z"),
                new ScoreDashboardResponse.LessonPlaceholder("Practice Session: Vocabulary", "2026-07-17T14:00:00Z"));

        List<ScoreDashboardResponse.NotificationPlaceholder> notifications = List.of(
                new ScoreDashboardResponse.NotificationPlaceholder(
                        "Welcome", "Welcome to the KBV Education companion platform.", "2026-07-09T09:00:00Z"));

        return new ScoreDashboardResponse(
                student.getFullName(),
                student.getRole().getName(),
                cohortInfo,
                score.compositeScore().doubleValue(),
                score.practicePercentage().doubleValue(),
                score.reflectionPercentage().doubleValue(),
                score.homeworkPercentage().doubleValue(),
                score.quizPercentage().doubleValue(),
                displayTier,
                lessons,
                notifications,
                pace,
                attendance,
                weights);
    }

    /** Last {@link #ATTENDANCE_WINDOW_DAYS} calendar days, one row per day — "active" means the
     * student practiced or reflected that day; voided (admin-excused) days and Rest/Skip days
     * (cohort-configured, see {@link com.kbv.education.entity.CohortDay}) are flagged separately
     * so the frontend can exclude them from the "showed up" denominator rather than counting them
     * as a miss. {@code cohortId} may be null (no active cohort) — no dates are then flagged as
     * Rest/Skip. */
    private List<ScoreDashboardResponse.AttendanceDay> buildAttendance(UUID studentId, UUID cohortId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(ATTENDANCE_WINDOW_DAYS - 1L);
        List<StudyDay> days = studyDayRepository
                .findByStudent_IdAndStudyDateBetweenAndDeletedFalseOrderByStudyDateAsc(studentId, start, today);
        java.util.Map<LocalDate, StudyDay> byDate = new java.util.HashMap<>();
        days.forEach(d -> byDate.put(d.getStudyDate(), d));

        java.util.Set<LocalDate> nonLessonDates = cohortId == null
                ? java.util.Set.of()
                : cohortDayRepository.findByCohort_IdAndDateBetweenAndDeletedFalse(cohortId, start, today).stream()
                        .filter(d -> CohortDayType.NON_LESSON_TYPES.contains(d.getDayType()))
                        .map(CohortDay::getDate)
                        .collect(java.util.stream.Collectors.toSet());

        List<ScoreDashboardResponse.AttendanceDay> result = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            StudyDay sd = byDate.get(day);
            boolean active = sd != null && !sd.isVoided() && (sd.isHasPractice() || sd.isHasReflection());
            boolean voided = sd != null && sd.isVoided();
            boolean restOrSkip = nonLessonDates.contains(day);
            result.add(new ScoreDashboardResponse.AttendanceDay(day, active, voided, restOrSkip));
        }
        return result;
    }
}
