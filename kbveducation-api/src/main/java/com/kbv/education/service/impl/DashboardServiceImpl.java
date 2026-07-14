package com.kbv.education.service.impl;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.dto.tier.CurrentTierResponse;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.CohortMapper;
import com.kbv.education.mapper.UserMapper;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.UserSessionRepository;
import com.kbv.education.service.DashboardService;
import com.kbv.education.service.ScoreEngineService;
import com.kbv.education.service.TierEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final UserSessionRepository userSessionRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final ParentStudentRepository parentStudentRepository;
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

        return new AdminDashboardResponse(totalStudents, totalParents, totalCohorts,
                activeCohorts, inactiveCohorts, todaysLogins, recentUsers, recentCohorts);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoreDashboardResponse studentDashboard(UUID studentUserId) {
        User student = userRepository.findByIdAndDeletedFalse(studentUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentUserId));
        return buildScoreDashboard(student);
    }

    @Override
    @Transactional(readOnly = true)
    public ScoreDashboardResponse parentDashboard(UUID parentUserId) {
        User student = parentStudentRepository.findByParent_IdAndDeletedFalse(parentUserId)
                .map(link -> link.getStudent())
                .orElseThrow(() -> new BusinessRuleException("No student is linked to this parent account"));
        return buildScoreDashboard(student);
    }

    private ScoreDashboardResponse buildScoreDashboard(User student) {
        ScoreDashboardResponse.CohortInfo cohortInfo = studentCohortRepository
                .findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId())
                .map(sc -> new ScoreDashboardResponse.CohortInfo(
                        sc.getCohort().getName(), sc.getCohort().getStatus().name()))
                .orElse(null);

        StudentScoreResponse score = scoreEngineService.getCurrent(student.getId());
        CurrentTierResponse tier = tierEngineService.getCurrentTier(student.getId());
        // The confirmed/overridden tier is the official one once an admin has acted on it;
        // otherwise fall back to the system-calculated tier.
        String displayTier = tier.confirmedTier() != null ? tier.confirmedTier() : tier.calculatedTier();

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
                notifications);
    }
}
