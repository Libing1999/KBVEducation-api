package com.kbv.education.service.impl;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.dto.homework.HomeworkSubmissionResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Homework;
import com.kbv.education.entity.HomeworkSubmission;
import com.kbv.education.entity.HomeworkSubmissionFile;
import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.FileMapper;
import com.kbv.education.repository.HomeworkRepository;
import com.kbv.education.repository.HomeworkSubmissionFileRepository;
import com.kbv.education.repository.HomeworkSubmissionRepository;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.HomeworkSubmissionSpecifications;
import com.kbv.education.service.ActivityService;
import com.kbv.education.service.HomeworkSubmissionService;
import com.kbv.education.service.NotificationService;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import com.kbv.education.utils.MimeTypes;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkSubmissionServiceImpl implements HomeworkSubmissionService {

    private static final String SUBDIR = "homework";
    private static final List<String> SORTABLE = List.of("submittedAt", "createdAt");
    /** Fallback allowed types when a homework does not restrict them. */
    private static final Set<String> DEFAULT_ALLOWED =
            Set.of("jpg", "jpeg", "png", "pdf", "doc", "docx", "mp3", "m4a", "webm", "mp4");

    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkSubmissionFileRepository submissionFileRepository;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    @Override
    @Transactional
    public HomeworkSubmissionResponse submit(UUID userId, UUID lessonId, String note, MultipartFile[] files) {
        User student = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", userId));

        Homework homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("No homework configured for this lesson"));
        assertLessonAccessible(userId, homework.getLesson());

        if (submissionRepository.existsByHomework_IdAndStudent_IdAndDeletedFalse(homework.getId(), userId)) {
            throw new BusinessRuleException("You have already submitted this homework");
        }
        if (files == null || files.length == 0) {
            throw new BadRequestException("At least one file is required");
        }

        Set<String> allowed = resolveAllowed(homework.getAllowedFileTypes());
        long maxBytes = homework.getMaxFileSizeMb() != null
                ? (long) homework.getMaxFileSizeMb() * 1024 * 1024
                : Long.MAX_VALUE;

        // Validate everything before storing anything.
        for (MultipartFile file : files) {
            String original = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            String ext = extensionOf(original);
            if (!allowed.contains(ext)) {
                throw new BadRequestException("File type '" + ext + "' is not allowed. Allowed: " + allowed);
            }
            if (file.getSize() > maxBytes) {
                throw new BadRequestException(
                        "File '" + original + "' exceeds the maximum size of " + homework.getMaxFileSizeMb() + " MB");
            }
        }

        HomeworkSubmission submission = new HomeworkSubmission();
        submission.setHomework(homework);
        submission.setStudent(student);
        submission.setNote(note);
        HomeworkSubmission saved = submissionRepository.save(submission);

        for (MultipartFile file : files) {
            String original = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            StoredFile stored = fileStorageService.store(file, SUBDIR);
            HomeworkSubmissionFile entity = new HomeworkSubmissionFile();
            entity.setSubmission(saved);
            entity.setFileName(original);
            entity.setStoredName(stored.storedName());
            entity.setFileType(extensionOf(original));
            entity.setFileSize(stored.size());
            submissionFileRepository.save(entity);
        }

        notificationService.notifyAdmins(NotificationType.HOMEWORK_SUBMITTED, "Homework Submitted",
                student.getFullName() + " submitted homework for " + homework.getLesson().getTitle(),
                ReferenceType.HOMEWORK, homework.getId());

        activityService.record(userId, ActivityType.HOMEWORK_SUBMITTED, "Homework submitted",
                homework.getLesson().getTitle(), ReferenceType.HOMEWORK,
                homework.getLesson().getId(), java.time.LocalDate.now());

        log.info("Student {} submitted homework for lesson {}", userId, lessonId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public HomeworkSubmissionResponse myByLesson(UUID userId, UUID lessonId) {
        UUID studentId = resolveStudentId(userId);
        Homework homework = homeworkRepository.findByLesson_IdAndDeletedFalse(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("No homework configured for this lesson"));
        HomeworkSubmission submission = submissionRepository
                .findByHomework_IdAndStudent_IdAndDeletedFalse(homework.getId(), studentId)
                .orElseThrow(() -> new ResourceNotFoundException("No submission for this lesson yet"));
        return toResponse(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeworkSubmissionResponse> myAll(UUID userId) {
        UUID studentId = resolveStudentId(userId);
        return submissionRepository.findByStudent_IdAndDeletedFalseOrderBySubmittedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HomeworkSubmissionResponse> adminList(UUID lessonId, UUID studentId, String search,
                                                              int page, int size, String sort, String direction) {
        Specification<HomeworkSubmission> spec = Specification.where(HomeworkSubmissionSpecifications.notDeleted())
                .and(HomeworkSubmissionSpecifications.inLesson(lessonId))
                .and(HomeworkSubmissionSpecifications.forStudent(studentId))
                .and(HomeworkSubmissionSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<HomeworkSubmission> result = submissionRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadFile(UUID fileId) {
        return buildDownload(loadFile(fileId));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadMyFile(UUID userId, UUID fileId) {
        UUID studentId = resolveStudentId(userId);
        HomeworkSubmissionFile file = loadFile(fileId);
        if (!file.getSubmission().getStudent().getId().equals(studentId)) {
            throw ResourceNotFoundException.of("File", fileId);
        }
        return buildDownload(file);
    }

    // --- helpers -----------------------------------------------------------

    private HomeworkSubmissionFile loadFile(UUID fileId) {
        return submissionFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
    }

    private FileDownloadResult buildDownload(HomeworkSubmissionFile file) {
        Resource resource = fileStorageService.loadAsResource(SUBDIR, file.getStoredName());
        return new FileDownloadResult(
                file.getFileName(),
                MimeTypes.forExtension(file.getFileType()),
                file.getFileSize() == null ? 0 : file.getFileSize(),
                resource);
    }

    private HomeworkSubmissionResponse toResponse(HomeworkSubmission submission) {
        Homework homework = submission.getHomework();
        Lesson lesson = homework.getLesson();
        User student = submission.getStudent();
        List<FileResponse> files =
                submissionFileRepository.findBySubmission_IdAndDeletedFalseOrderByUploadedDateAsc(submission.getId())
                        .stream()
                        .map(fileMapper::toResponse)
                        .toList();
        return new HomeworkSubmissionResponse(
                submission.getId(),
                homework.getId(),
                lesson.getId(),
                lesson.getTitle(),
                student.getId(),
                student.getFullName(),
                submission.getNote(),
                submission.getSubmittedAt(),
                files);
    }

    private void assertLessonAccessible(UUID studentId, Lesson lesson) {
        UUID cohortId = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getId())
                .orElse(null);
        if (!lesson.isPublished() || lesson.isDeleted() || cohortId == null
                || !lesson.getCohort().getId().equals(cohortId)) {
            throw ResourceNotFoundException.of("Lesson", lesson.getId());
        }
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
        throw new BusinessRuleException("Homework is available to students and parents only");
    }

    private Set<String> resolveAllowed(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_ALLOWED;
        }
        return Arrays.stream(csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT).replace(".", ""))
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    private String extensionOf(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }
}
