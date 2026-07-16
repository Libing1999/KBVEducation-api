package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.lesson.CreateLessonRequest;
import com.kbv.education.dto.lesson.LessonResponse;
import com.kbv.education.dto.lesson.UpdateLessonRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.LessonStatus;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.LessonMapper;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.LessonFileRepository;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.repository.spec.LessonSpecifications;
import com.kbv.education.service.LessonService;
import com.kbv.education.service.NotificationService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonServiceImpl implements LessonService {

    private static final List<String> SORTABLE =
            List.of("displayOrder", "lessonNumber", "title", "lessonDate", "createdAt", "publishedDate", "status");

    private final LessonRepository lessonRepository;
    private final CohortRepository cohortRepository;
    private final QuizRepository quizRepository;
    private final HomeworkRepository homeworkRepository;
    private final LessonFileRepository lessonFileRepository;
    private final LessonMapper lessonMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LessonResponse> list(UUID cohortId, LessonStatus status, String search,
                                             int page, int size, String sort, String direction) {
        Specification<Lesson> spec = Specification.where(LessonSpecifications.notDeleted())
                .and(LessonSpecifications.inCohort(cohortId))
                .and(LessonSpecifications.hasStatus(status))
                .and(LessonSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<Lesson> result = lessonRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonResponse get(UUID id) {
        return toResponse(getLesson(id));
    }

    @Override
    @Transactional
    @Audited(action = "LESSON_CREATED", entityType = "LESSON")
    public LessonResponse create(CreateLessonRequest request) {
        Cohort cohort = cohortRepository.findByIdAndDeletedFalse(request.cohortId())
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", request.cohortId()));

        Lesson lesson = new Lesson();
        lesson.setCohort(cohort);
        lesson.setLessonNumber(request.lessonNumber());
        lesson.setTitle(request.title());
        lesson.setSummary(request.summary());
        lesson.setDescription(request.description());
        lesson.setLessonDate(request.lessonDate());
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder()
                : nextDisplayOrder(cohort.getId()));

        Lesson saved = lessonRepository.save(lesson);
        log.info("Created lesson '{}' in cohort {}", saved.getTitle(), cohort.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LessonResponse update(UUID id, UpdateLessonRequest request) {
        Lesson lesson = getLesson(id);
        lesson.setLessonNumber(request.lessonNumber());
        lesson.setTitle(request.title());
        lesson.setSummary(request.summary());
        lesson.setDescription(request.description());
        lesson.setLessonDate(request.lessonDate());
        return toResponse(lessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Lesson lesson = getLesson(id);
        lesson.setDeleted(true);
        lessonRepository.save(lesson);
        log.info("Soft-deleted lesson {}", id);
    }

    @Override
    @Transactional
    public LessonResponse publish(UUID id) {
        Lesson lesson = getLesson(id);
        boolean wasPublished = lesson.isPublished();
        lesson.setStatus(LessonStatus.PUBLISHED);
        lesson.setPublishedDate(Instant.now());
        Lesson saved = lessonRepository.save(lesson);
        if (!wasPublished) {
            notificationService.notifyCohortStudents(saved.getCohort().getId(),
                    NotificationType.NEW_LESSON_PUBLISHED, "New Lesson Published", saved.getTitle(),
                    ReferenceType.LESSON, saved.getId());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LessonResponse unpublish(UUID id) {
        Lesson lesson = getLesson(id);
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setPublishedDate(null);
        return toResponse(lessonRepository.save(lesson));
    }

    @Override
    @Transactional
    public LessonResponse duplicate(UUID id) {
        Lesson original = getLesson(id);
        Lesson copy = new Lesson();
        copy.setCohort(original.getCohort());
        copy.setLessonNumber(original.getLessonNumber());
        copy.setTitle(original.getTitle() + " (Copy)");
        copy.setSummary(original.getSummary());
        copy.setDescription(original.getDescription());
        copy.setLessonDate(original.getLessonDate());
        copy.setStatus(LessonStatus.DRAFT);
        copy.setDisplayOrder(nextDisplayOrder(original.getCohort().getId()));
        Lesson saved = lessonRepository.save(copy);
        log.info("Duplicated lesson {} -> {}", id, saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void reorder(ReorderRequest request) {
        for (ReorderRequest.Item item : request.items()) {
            Lesson lesson = getLesson(item.id());
            lesson.setDisplayOrder(item.displayOrder());
            lessonRepository.save(lesson);
        }
    }

    private int nextDisplayOrder(UUID cohortId) {
        return lessonRepository.findFirstByCohort_IdAndDeletedFalseOrderByDisplayOrderDesc(cohortId)
                .map(l -> l.getDisplayOrder() + 1)
                .orElse(0);
    }

    private LessonResponse toResponse(Lesson lesson) {
        long fileCount = lessonFileRepository.countByLesson_IdAndDeletedFalse(lesson.getId());
        boolean hasQuiz = quizRepository.existsByLesson_IdAndDeletedFalse(lesson.getId());
        boolean hasHomework = homeworkRepository.existsByLesson_IdAndDeletedFalse(lesson.getId());
        return lessonMapper.toResponse(lesson, fileCount, hasQuiz, hasHomework);
    }

    private Lesson getLesson(UUID id) {
        return lessonRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", id));
    }
}
