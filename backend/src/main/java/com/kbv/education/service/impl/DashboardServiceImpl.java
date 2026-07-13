package com.kbv.education.service.impl;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;
import com.kbv.education.dto.response.UserResponse;
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

    /**
     * Builds the score dashboard for a student. Scores are deterministic dummy
     * values in Phase 1 (derived from the id so they are stable per student);
     * real scoring will populate this shape in a later phase.
     */
    private ScoreDashboardResponse buildScoreDashboard(User student) {
        ScoreDashboardResponse.CohortInfo cohortInfo = studentCohortRepository
                .findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId())
                .map(sc -> new ScoreDashboardResponse.CohortInfo(
                        sc.getCohort().getName(), sc.getCohort().getStatus().name()))
                .orElse(null);

        int seed = Math.abs(student.getId().hashCode());
        double practice = 60 + seed % 40;
        double reflection = 55 + (seed / 3) % 45;
        double homework = 65 + (seed / 7) % 35;
        double quiz = 50 + (seed / 11) % 50;
        double composite = Math.round((practice + reflection + homework + quiz) / 4.0 * 10) / 10.0;
        String tier = composite >= 85 ? "Gold" : composite >= 70 ? "Silver" : "Bronze";

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
                composite,
                practice,
                reflection,
                homework,
                quiz,
                tier,
                lessons,
                notifications);
    }
}
