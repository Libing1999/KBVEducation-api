package com.kbv.education.service.impl;

import com.kbv.education.dto.dashboard.ActivityLogResponse;
import com.kbv.education.dto.dashboard.StudyDayResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.ActivityLog;
import com.kbv.education.entity.StudyDay;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.repository.ActivityLogRepository;
import com.kbv.education.repository.StudyDayRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

    private static final int MAX_PAGE = 100;

    private final ActivityLogRepository activityLogRepository;
    private final StudyDayRepository studyDayRepository;
    private final UserRepository userRepository;

    /**
     * Best-effort recording, isolated in its own transaction so a failure here
     * cannot roll back the caller's core operation (submitting a reflection,
     * logging practice, etc.).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID studentId, ActivityType type, String title, String description,
                       ReferenceType referenceType, UUID referenceId, LocalDate date) {
        try {
            User student = userRepository.findByIdAndDeletedFalse(studentId).orElse(null);
            if (student == null) {
                return;
            }

            ActivityLog log = new ActivityLog();
            log.setStudent(student);
            log.setActivityType(type);
            log.setTitle(title);
            log.setDescription(description);
            log.setReferenceType(referenceType);
            log.setReferenceId(referenceId);
            log.setOccurredAt(Instant.now());
            activityLogRepository.save(log);

            if (date != null) {
                StudyDay day = studyDayRepository.findByStudent_IdAndStudyDateAndDeletedFalse(studentId, date)
                        .orElseGet(() -> {
                            StudyDay d = new StudyDay();
                            d.setStudent(student);
                            d.setStudyDate(date);
                            return d;
                        });
                switch (type) {
                    case REFLECTION_SUBMITTED -> day.setHasReflection(true);
                    case PRACTICE_LOGGED -> day.setHasPractice(true);
                    case HOMEWORK_SUBMITTED -> day.setHasHomework(true);
                    case QUIZ_COMPLETED -> day.setHasQuiz(true);
                    default -> { /* review events don't mark a study day */ }
                }
                studyDayRepository.save(day);
            }
        } catch (Exception e) {
            log.warn("Failed to record activity {} for student {}: {}", type, studentId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityLogResponse> recent(UUID studentId, int limit) {
        return activityLogRepository.findTop10ByStudent_IdAndDeletedFalseOrderByOccurredAtDesc(studentId).stream()
                .limit(limit <= 0 ? 10 : limit)
                .map(this::toActivityResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> list(UUID studentId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<ActivityLog> result =
                activityLogRepository.findByStudent_IdAndDeletedFalseOrderByOccurredAtDesc(studentId, pageable);
        return PageResponse.from(result, this::toActivityResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudyDayResponse> calendar(UUID studentId, LocalDate from, LocalDate to) {
        return studyDayRepository
                .findByStudent_IdAndStudyDateBetweenAndDeletedFalseOrderByStudyDateAsc(studentId, from, to).stream()
                .map(d -> new StudyDayResponse(
                        d.getStudyDate(),
                        d.isHasReflection(),
                        d.isHasPractice(),
                        d.isHasHomework(),
                        d.isHasQuiz()))
                .toList();
    }

    private ActivityLogResponse toActivityResponse(ActivityLog a) {
        return new ActivityLogResponse(
                a.getId(),
                a.getActivityType(),
                a.getTitle(),
                a.getDescription(),
                a.getReferenceType(),
                a.getReferenceId(),
                a.getOccurredAt());
    }
}
