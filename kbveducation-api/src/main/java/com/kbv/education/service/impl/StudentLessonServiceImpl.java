package com.kbv.education.service.impl;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.lesson.StudentLessonDetailResponse;
import com.kbv.education.dto.lesson.StudentLessonResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Homework;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.LessonFile;
import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.LessonStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.FileMapper;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.LessonFileRepository;
import com.kbv.education.repository.LessonRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.QuizAttemptRepository;
import com.kbv.education.repository.QuizRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.LessonSpecifications;
import com.kbv.education.service.LessonFileService;
import com.kbv.education.service.StudentLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentLessonServiceImpl implements StudentLessonService {

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final LessonRepository lessonRepository;
    private final LessonFileRepository lessonFileRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final LessonFileService lessonFileService;
    private final FileMapper fileMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentLessonResponse> myLessons(UUID userId, int page, int size) {
        UUID studentId = resolveStudentId(userId);
        UUID cohortId = activeCohortId(studentId).orElse(null);

        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.ASC, "displayOrder", "lessonNumber"));

        if (cohortId == null) {
            return PageResponse.from(new PageImpl<>(List.of(), pageable, 0));
        }

        Specification<Lesson> spec = Specification.where(LessonSpecifications.notDeleted())
                .and(LessonSpecifications.inCohort(cohortId))
                .and(LessonSpecifications.hasStatus(LessonStatus.PUBLISHED));

        Page<Lesson> result = lessonRepository.findAll(spec, pageable);
        return PageResponse.from(result, lesson -> toCard(lesson, studentId));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentLessonDetailResponse getLessonDetail(UUID userId, UUID lessonId) {
        UUID studentId = resolveStudentId(userId);
        Lesson lesson = accessibleLesson(studentId, lessonId);

        List<com.kbv.education.dto.file.FileResponse> files =
                lessonFileRepository.findByLesson_IdAndDeletedFalseOrderByUploadedDateAsc(lessonId).stream()
                        .map(fileMapper::toResponse)
                        .toList();

        Optional<Quiz> quiz = quizRepository.findByLesson_IdAndDeletedFalse(lessonId)
                .filter(Quiz::isPublished);
        boolean hasQuiz = quiz.isPresent();
        boolean quizCompleted = quiz
                .map(q -> quizAttemptRepository.existsByQuiz_IdAndStudent_IdAndDeletedFalse(q.getId(), studentId))
                .orElse(false);

        Optional<Homework> homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lessonId);
        boolean hasHomework = homework.isPresent();
        boolean homeworkSubmitted = homework
                .map(h -> homeworkSubmissionRepository
                        .existsByHomework_IdAndStudent_IdAndDeletedFalse(h.getId(), studentId))
                .orElse(false);

        return new StudentLessonDetailResponse(
                lesson.getId(),
                lesson.getLessonNumber(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getDescription(),
                lesson.getLessonDate(),
                files,
                hasQuiz,
                quiz.map(Quiz::getId).orElse(null),
                quiz.map(Quiz::getTitle).orElse(null),
                quizCompleted,
                hasHomework,
                homework.map(Homework::getId).orElse(null),
                homework.map(Homework::getTitle).orElse(null),
                homework.map(Homework::getInstructions).orElse(null),
                homework.map(Homework::getDueDate).orElse(null),
                homework.map(h -> splitTypes(h.getAllowedFileTypes())).orElse(List.of()),
                homework.map(Homework::getMaxFileSizeMb).orElse(null),
                homeworkSubmitted);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadLessonFile(UUID userId, UUID lessonId, UUID fileId) {
        UUID studentId = resolveStudentId(userId);
        accessibleLesson(studentId, lessonId);

        LessonFile file = lessonFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        if (!file.getLesson().getId().equals(lessonId)) {
            throw ResourceNotFoundException.of("File", fileId);
        }
        return lessonFileService.download(fileId);
    }

    // --- helpers -----------------------------------------------------------

    private StudentLessonResponse toCard(Lesson lesson, UUID studentId) {
        long fileCount = lessonFileRepository.countByLesson_IdAndDeletedFalse(lesson.getId());

        Optional<Quiz> quiz = quizRepository.findByLesson_IdAndDeletedFalse(lesson.getId())
                .filter(Quiz::isPublished);
        boolean quizCompleted = quiz
                .map(q -> quizAttemptRepository.existsByQuiz_IdAndStudent_IdAndDeletedFalse(q.getId(), studentId))
                .orElse(false);

        Optional<Homework> homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lesson.getId());
        boolean homeworkSubmitted = homework
                .map(h -> homeworkSubmissionRepository
                        .existsByHomework_IdAndStudent_IdAndDeletedFalse(h.getId(), studentId))
                .orElse(false);

        return new StudentLessonResponse(
                lesson.getId(),
                lesson.getLessonNumber(),
                lesson.getTitle(),
                lesson.getSummary(),
                lesson.getLessonDate(),
                fileCount,
                quiz.isPresent(),
                quizCompleted,
                homework.isPresent(),
                homeworkSubmitted);
    }

    /** Loads a lesson and verifies it is published and in the student's active cohort. */
    private Lesson accessibleLesson(UUID studentId, UUID lessonId) {
        Lesson lesson = lessonRepository.findByIdAndDeletedFalse(lessonId)
                .orElseThrow(() -> ResourceNotFoundException.of("Lesson", lessonId));
        UUID cohortId = activeCohortId(studentId).orElse(null);
        if (!lesson.isPublished() || cohortId == null || !lesson.getCohort().getId().equals(cohortId)) {
            // Do not reveal existence of lessons outside the student's scope.
            throw ResourceNotFoundException.of("Lesson", lessonId);
        }
        return lesson;
    }

    private UUID resolveStudentId(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        RoleType role = user.getRole().getName();
        if (role == RoleType.STUDENT) {
            return user.getId();
        }
        if (role == RoleType.PARENT) {
            return parentStudentRepository.findByParent_IdAndDeletedFalse(userId)
                    .map(link -> link.getStudent().getId())
                    .orElseThrow(() -> new BusinessRuleException("No student is linked to this parent account"));
        }
        throw new BusinessRuleException("Lessons are available to students and parents only");
    }

    private Optional<UUID> activeCohortId(UUID studentId) {
        return studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getId());
    }

    private List<String> splitTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
}
