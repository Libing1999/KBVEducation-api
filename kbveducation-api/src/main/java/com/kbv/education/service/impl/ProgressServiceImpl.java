package com.kbv.education.service.impl;

import com.kbv.education.dto.dashboard.AdminStatisticsResponse;
import com.kbv.education.dto.dashboard.ProgressMetrics;
import com.kbv.education.dto.dashboard.StudentProgressResponse;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.entity.ParentStudent;
import com.kbv.education.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgressServiceImpl implements ProgressService {

    private final ReflectionEntryRepository reflectionRepository;
    private final PracticeSessionRepository practiceRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;

    @Override
    @Transactional(readOnly = true)
    public StudentProgressResponse getProgress(UUID userId, UUID requestedStudentId) {
        return getProgressForStudent(resolveStudentId(userId, requestedStudentId));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentProgressResponse getProgressForStudent(UUID studentId) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        LocalDate today = LocalDate.now();
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate monthToInc = monthFrom.plusMonths(1).minusDays(1);
        Instant monthStart = monthFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd = monthFrom.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        ProgressMetrics month = new ProgressMetrics(
                reflectionRepository.countByStudent_IdAndReflectionDateBetweenAndDeletedFalse(studentId, monthFrom, monthToInc),
                practiceRepository.countDistinctStudyDaysBetween(studentId, monthFrom, monthToInc),
                submissionRepository.countByStudent_IdAndSubmittedAtBetweenAndDeletedFalse(studentId, monthStart, monthEnd),
                attemptRepository.countByStudent_IdAndSubmittedAtBetweenAndDeletedFalse(studentId, monthStart, monthEnd),
                union(attemptRepository.completedLessonIdsBetween(studentId, monthStart, monthEnd),
                        submissionRepository.submittedLessonIdsBetween(studentId, monthStart, monthEnd)));

        ProgressMetrics total = new ProgressMetrics(
                reflectionRepository.countByStudent_IdAndDeletedFalse(studentId),
                practiceRepository.countDistinctStudyDays(studentId),
                submissionRepository.countByStudent_IdAndDeletedFalse(studentId),
                attemptRepository.countByStudent_IdAndDeletedFalse(studentId),
                union(attemptRepository.completedLessonIds(studentId),
                        submissionRepository.submittedLessonIds(studentId)));

        int reflectionStreak = streak(new HashSet<>(reflectionRepository.reflectionDates(studentId)), today);
        int practiceStreak = streak(new HashSet<>(practiceRepository.practiceDates(studentId)), today);

        return new StudentProgressResponse(
                student.getId(), student.getFullName(), month, total, reflectionStreak, practiceStreak);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminStatisticsResponse adminStatistics() {
        LocalDate today = LocalDate.now();
        LocalDate weekFrom = today.minusDays(6);
        LocalDate monthFrom = today.minusDays(29);

        long weekly = reflectionRepository.countByReflectionDateBetweenAndDeletedFalse(weekFrom, today)
                + practiceRepository.countByStudyDateBetweenAndDeletedFalse(weekFrom, today);
        long monthly = reflectionRepository.countByReflectionDateBetweenAndDeletedFalse(monthFrom, today)
                + practiceRepository.countByStudyDateBetweenAndDeletedFalse(monthFrom, today);

        return new AdminStatisticsResponse(
                reflectionRepository.countByReflectionDateAndDeletedFalse(today),
                practiceRepository.countByStudyDateAndDeletedFalse(today),
                practiceRepository.countByStatusAndDeletedFalse(PracticeStatus.PENDING_REVIEW),
                practiceRepository.countByStatusAndDeletedFalse(PracticeStatus.APPROVED),
                practiceRepository.countByStatusAndDeletedFalse(PracticeStatus.REJECTED),
                userRepository.countByRole_NameAndStatusAndDeletedFalse(RoleType.STUDENT, UserStatus.ACTIVE),
                weekly,
                monthly);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID resolveStudentId(UUID userId, UUID requestedStudentId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        RoleType role = user.getRole().getName();
        if (role == RoleType.STUDENT) {
            return user.getId();
        }
        if (role == RoleType.PARENT) {
            List<ParentStudent> links = parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(userId);
            if (links.isEmpty()) {
                throw new BusinessRuleException("No student is linked to this parent account");
            }
            if (requestedStudentId == null) {
                return links.get(0).getStudent().getId();
            }
            return links.stream()
                    .map(link -> link.getStudent().getId())
                    .filter(id -> id.equals(requestedStudentId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException("This student is not linked to your account"));
        }
        throw new BusinessRuleException("Progress is available to students and parents only");
    }

    // --- helpers -----------------------------------------------------------

    private long union(java.util.List<UUID> a, java.util.List<UUID> b) {
        Set<UUID> set = new HashSet<>(a);
        set.addAll(b);
        return set.size();
    }

    /** Current consecutive-day streak ending today (or yesterday, if today isn't done yet). */
    private int streak(Set<LocalDate> days, LocalDate today) {
        if (days.isEmpty()) {
            return 0;
        }
        LocalDate cursor = today;
        if (!days.contains(cursor)) {
            cursor = today.minusDays(1);
        }
        if (!days.contains(cursor)) {
            return 0;
        }
        int count = 0;
        while (days.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }
}
