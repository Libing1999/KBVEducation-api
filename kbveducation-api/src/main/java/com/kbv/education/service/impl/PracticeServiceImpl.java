package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.dto.practice.AdminUpdatePracticeRequest;
import com.kbv.education.dto.practice.PracticeSessionResponse;
import com.kbv.education.dto.practice.ReviewRequestAdminSummary;
import com.kbv.education.dto.practice.ReviewRequestResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.PracticeFile;
import com.kbv.education.entity.PracticeReviewRequest;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.ReviewRequestStatus;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.FileMapper;
import com.kbv.education.repository.PracticeFileRepository;
import com.kbv.education.repository.PracticeReviewRequestRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.repository.spec.PracticeSessionSpecifications;
import com.kbv.education.service.ActivityService;
import com.kbv.education.service.NotificationService;
import com.kbv.education.service.PracticeService;
import com.kbv.education.service.TierEngineService;
import com.kbv.education.service.ai.PracticeValidationService;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import com.kbv.education.service.storage.VirusScanner;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    private static final String SUBDIR = "practice";
    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "png", "jpg", "jpeg");
    private static final int MAX_FILE_MB = 25;
    private static final int MAX_NOTES_LEN = 2000;
    private static final int MAX_TRANSCRIPT_LEN = 5000;
    private static final Set<StudyType> PAST_PAPER_TYPES =
            Set.of(StudyType.PAST_PAPER, StudyType.PAST_PAPER_TEST_DAY, StudyType.PAST_PAPER_IMPROVEMENT_DAY);
    private static final List<String> SORTABLE = List.of("studyDate", "createdAt", "status");

    private final PracticeSessionRepository practiceRepository;
    private final PracticeFileRepository practiceFileRepository;
    private final PracticeReviewRequestRepository reviewRequestRepository;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final FileStorageService fileStorageService;
    private final FileMapper fileMapper;
    private final VirusScanner virusScanner;
    private final PracticeValidationService validationService;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final TierEngineService tierEngineService;

    @Override
    @Transactional
    public PracticeSessionResponse create(UUID studentId, LocalDate studyDate, String subject, int durationMinutes,
                                          StudyType studyType, String notes, String transcript, Integer year,
                                          MultipartFile[] files) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));

        if (studyDate == null) {
            throw new BadRequestException("Study date is required");
        }
        if (!StringUtils.hasText(subject)) {
            throw new BadRequestException("Subject is required");
        }
        if (studyType == null) {
            throw new BadRequestException("Study type is required");
        }
        if (durationMinutes <= 0) {
            throw new BadRequestException("Duration must be greater than zero");
        }
        if (notes != null && notes.length() > MAX_NOTES_LEN) {
            throw new BadRequestException("Notes must be " + MAX_NOTES_LEN + " characters or fewer");
        }
        if (transcript != null && transcript.length() > MAX_TRANSCRIPT_LEN) {
            throw new BadRequestException("Transcript must be " + MAX_TRANSCRIPT_LEN + " characters or fewer");
        }
        Integer effectiveYear = year;
        if (isPastPaperType(studyType)) {
            if (effectiveYear == null) {
                throw new BadRequestException("Year is required for this study type");
            }
        } else {
            effectiveYear = null;
        }

        // Validate attachments up-front (before storing anything).
        MultipartFile[] safeFiles = files == null ? new MultipartFile[0] : files;
        for (MultipartFile file : safeFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String ext = extensionOf(StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()));
            if (!ALLOWED.contains(ext)) {
                throw new BadRequestException("File type '" + ext + "' is not allowed. Allowed: " + ALLOWED);
            }
            if (file.getSize() > (long) MAX_FILE_MB * 1024 * 1024) {
                throw new BadRequestException("File exceeds the maximum size of " + MAX_FILE_MB + " MB");
            }
            virusScanner.scan(file);
        }

        PracticeSession session = new PracticeSession();
        session.setStudent(student);
        session.setStudyDate(studyDate);
        session.setSubject(subject.trim());
        session.setDurationMinutes(durationMinutes);
        session.setStudyType(studyType);
        session.setNotes(notes);
        session.setTranscript(transcript);
        session.setYear(effectiveYear);
        // Extension point — manual impl returns PENDING_REVIEW.
        session.setStatus(validationService.validate(session));
        PracticeSession saved = practiceRepository.save(session);

        for (MultipartFile file : safeFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String original = StringUtils.cleanPath(
                    file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            StoredFile stored = fileStorageService.store(file, SUBDIR);
            PracticeFile pf = new PracticeFile();
            pf.setPracticeSession(saved);
            pf.setFileName(original);
            pf.setStoredName(stored.storedName());
            pf.setFileType(extensionOf(original));
            pf.setFileSize(stored.size());
            practiceFileRepository.save(pf);
        }

        activityService.record(studentId, ActivityType.PRACTICE_LOGGED, "Practice logged",
                saved.getSubject() + " (" + durationMinutes + " min)", ReferenceType.PRACTICE,
                saved.getId(), studyDate);

        notificationService.notifyAdmins(NotificationType.PRACTICE_SUBMITTED, "Practice Submitted",
                student.getFullName() + " logged practice: " + saved.getSubject(),
                ReferenceType.PRACTICE, saved.getId());

        log.info("Student {} logged practice session {} ({} min, {})", studentId, saved.getId(), durationMinutes, studyType);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticeSessionResponse> getMine(UUID studentId) {
        return practiceRepository.findByStudent_IdAndDeletedFalseOrderByStudyDateDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeSessionResponse getMineById(UUID studentId, UUID id) {
        return toResponse(loadOwned(studentId, id));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadMyFile(UUID studentId, UUID fileId) {
        PracticeFile file = practiceFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        if (!file.getPracticeSession().getStudent().getId().equals(studentId)) {
            throw ResourceNotFoundException.of("File", fileId);
        }
        return buildDownload(file);
    }

    @Override
    @Transactional
    public ReviewRequestResponse requestReview(UUID studentId, UUID practiceId, String reason) {
        PracticeSession session = loadOwned(studentId, practiceId);
        if (session.getStatus() != PracticeStatus.REJECTED) {
            throw new BusinessRuleException("A re-review can only be requested for a rejected session");
        }
        if (reviewRequestRepository.existsByPracticeSession_IdAndStatusAndDeletedFalse(
                practiceId, ReviewRequestStatus.PENDING)) {
            throw new BusinessRuleException("A review request is already pending for this session");
        }

        PracticeReviewRequest request = new PracticeReviewRequest();
        request.setPracticeSession(session);
        request.setStudent(session.getStudent());
        request.setReason(reason);
        request.setStatus(ReviewRequestStatus.PENDING);
        PracticeReviewRequest saved = reviewRequestRepository.save(request);

        notificationService.notifyAdmins(NotificationType.REVIEW_REQUESTED, "Review Requested",
                session.getStudent().getFullName() + " requested a re-review for " + session.getSubject(),
                ReferenceType.PRACTICE, session.getId());

        log.info("Student {} requested re-review for practice {}", studentId, practiceId);
        return toReviewResponse(saved);
    }

    // ========================= ADMIN =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PracticeSessionResponse> adminList(UUID cohortId, UUID studentId, PracticeStatus status,
                                                           StudyType studyType, String search,
                                                           int page, int size, String sort, String direction) {
        Specification<PracticeSession> spec = Specification.where(PracticeSessionSpecifications.notDeleted())
                .and(PracticeSessionSpecifications.inCohort(cohortId))
                .and(PracticeSessionSpecifications.forStudent(studentId))
                .and(PracticeSessionSpecifications.ofStatus(status))
                .and(PracticeSessionSpecifications.ofType(studyType))
                .and(PracticeSessionSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<PracticeSession> result = practiceRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PracticeSessionResponse adminGet(UUID id) {
        return toResponse(load(id));
    }

    @Override
    @Transactional
    @Audited(action = "PRACTICE_APPROVED", entityType = "PRACTICE_SESSION")
    public PracticeSessionResponse approve(UUID id, UUID adminId, String comment) {
        return decide(id, adminId, PracticeStatus.APPROVED, comment);
    }

    @Override
    @Transactional
    public PracticeSessionResponse reject(UUID id, UUID adminId, String comment) {
        return decide(id, adminId, PracticeStatus.REJECTED, comment);
    }

    @Override
    @Transactional
    public PracticeSessionResponse adminUpdate(UUID id, AdminUpdatePracticeRequest request) {
        PracticeSession session = load(id);
        session.setStudyDate(request.studyDate());
        session.setSubject(request.subject().trim());
        session.setDurationMinutes(request.durationMinutes());
        session.setStudyType(request.studyType());
        session.setNotes(request.notes());
        if (request.adminComment() != null) {
            session.setAdminComment(request.adminComment());
        }
        practiceRepository.save(session);
        return toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult adminDownloadFile(UUID fileId) {
        PracticeFile file = practiceFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> ResourceNotFoundException.of("File", fileId));
        return buildDownload(file);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewRequestAdminSummary> adminListReviewRequests(ReviewRequestStatus status,
                                                                           int page, int size) {
        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", List.of("createdAt"));
        Page<PracticeReviewRequest> result = status == null
                ? reviewRequestRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                : reviewRequestRepository.findByStatusAndDeletedFalseOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(result, this::toReviewSummary);
    }

    @Override
    @Transactional
    public ReviewRequestResponse resolveReviewRequest(UUID requestId, UUID adminId, boolean approve, String notes) {
        PracticeReviewRequest request = reviewRequestRepository.findByIdAndDeletedFalse(requestId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review request", requestId));
        if (request.getStatus() != ReviewRequestStatus.PENDING) {
            throw new BusinessRuleException("This review request has already been resolved");
        }
        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("Admin", adminId));

        request.setStatus(approve ? ReviewRequestStatus.APPROVED : ReviewRequestStatus.REJECTED);
        request.setAdminNotes(notes);
        request.setResolvedBy(admin);
        request.setResolvedAt(Instant.now());
        reviewRequestRepository.save(request);

        PracticeSession session = request.getPracticeSession();
        // Approving the request approves the underlying session.
        if (approve) {
            session.setStatus(PracticeStatus.APPROVED);
            session.setReviewedBy(admin);
            session.setReviewedAt(Instant.now());
            if (notes != null && !notes.isBlank()) {
                session.setAdminComment(notes);
            }
            practiceRepository.save(session);
        }

        notifyStudentOfDecision(session, approve);

        log.info("Admin {} {} review request {}", adminId, approve ? "approved" : "rejected", requestId);
        return toReviewResponse(request);
    }

    private PracticeSessionResponse decide(UUID id, UUID adminId, PracticeStatus status, String comment) {
        PracticeSession session = load(id);
        User admin = userRepository.findByIdAndDeletedFalse(adminId)
                .orElseThrow(() -> ResourceNotFoundException.of("Admin", adminId));
        session.setStatus(status);
        session.setAdminComment(comment);
        session.setReviewedBy(admin);
        session.setReviewedAt(Instant.now());
        practiceRepository.save(session);
        notifyStudentOfDecision(session, status == PracticeStatus.APPROVED);

        // Approval/rejection changes the "Full Papers" tier gate for past-paper sessions even
        // though it doesn't change Practice %; recalculate the tier only, best-effort.
        if (isPastPaperType(session.getStudyType())) {
            try {
                tierEngineService.recalculateCalculatedTier(session.getStudent().getId());
            } catch (Exception e) {
                log.warn("Failed to recalculate tier after practice decision for student {}: {}",
                        session.getStudent().getId(), e.getMessage());
            }
        }

        log.info("Admin {} set practice {} to {}", adminId, id, status);
        return toResponse(session);
    }

    /** Notify the owning student that their practice review was approved or rejected. */
    private void notifyStudentOfDecision(PracticeSession session, boolean approved) {
        notificationService.notify(
                session.getStudent().getId(),
                approved ? NotificationType.REVIEW_APPROVED : NotificationType.REVIEW_REJECTED,
                approved ? "Practice Approved" : "Practice Rejected",
                "Your practice \"" + session.getSubject() + "\" was " + (approved ? "approved" : "rejected"),
                ReferenceType.PRACTICE, session.getId());
    }

    private PracticeSession load(UUID id) {
        return practiceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Practice session", id));
    }

    private ReviewRequestAdminSummary toReviewSummary(PracticeReviewRequest r) {
        PracticeSession session = r.getPracticeSession();
        User student = r.getStudent();
        return new ReviewRequestAdminSummary(
                r.getId(),
                session.getId(),
                student.getId(),
                student.getFullName(),
                cohortNameOf(student.getId()),
                session.getSubject(),
                r.getReason(),
                r.getStatus(),
                r.getCreatedAt());
    }

    // --- helpers -----------------------------------------------------------

    private PracticeSession loadOwned(UUID studentId, UUID id) {
        PracticeSession session = practiceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Practice session", id));
        if (!session.getStudent().getId().equals(studentId)) {
            throw ResourceNotFoundException.of("Practice session", id);
        }
        return session;
    }

    private FileDownloadResult buildDownload(PracticeFile file) {
        Resource resource = fileStorageService.loadAsResource(SUBDIR, file.getStoredName());
        return new FileDownloadResult(
                file.getFileName(),
                MimeTypes.forExtension(file.getFileType()),
                file.getFileSize() == null ? 0 : file.getFileSize(),
                resource);
    }

    /** True for any study type ("legacy" or current) that represents a past-paper session. */
    private boolean isPastPaperType(StudyType studyType) {
        return PAST_PAPER_TYPES.contains(studyType);
    }

    private String extensionOf(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    private String cohortNameOf(UUID studentId) {
        return studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getName())
                .orElse(null);
    }

    private PracticeSessionResponse toResponse(PracticeSession s) {
        User student = s.getStudent();
        List<FileResponse> files = practiceFileRepository.findByPracticeSession_IdAndDeletedFalse(s.getId()).stream()
                .map(fileMapper::toResponse)
                .toList();
        List<ReviewRequestResponse> requests =
                reviewRequestRepository.findByPracticeSession_IdAndDeletedFalseOrderByCreatedAtDesc(s.getId()).stream()
                        .map(this::toReviewResponse)
                        .toList();
        return new PracticeSessionResponse(
                s.getId(),
                student.getId(),
                student.getFullName(),
                cohortNameOf(student.getId()),
                s.getStudyDate(),
                s.getSubject(),
                s.getDurationMinutes(),
                s.getStudyType(),
                s.getNotes(),
                s.getTranscript(),
                s.getYear(),
                s.getStatus(),
                s.getAdminComment(),
                s.getReviewedBy() == null ? null : s.getReviewedBy().getFullName(),
                s.getReviewedAt(),
                s.getCreatedAt(),
                files,
                requests);
    }

    private ReviewRequestResponse toReviewResponse(PracticeReviewRequest r) {
        return new ReviewRequestResponse(
                r.getId(),
                r.getStatus(),
                r.getReason(),
                r.getAdminNotes(),
                r.getResolvedBy() == null ? null : r.getResolvedBy().getFullName(),
                r.getResolvedAt(),
                r.getCreatedAt());
    }
}
