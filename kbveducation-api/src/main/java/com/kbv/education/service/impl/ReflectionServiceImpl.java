package com.kbv.education.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.reflection.AdminReflectionSummary;
import com.kbv.education.dto.reflection.ReflectionAnswerInput;
import com.kbv.education.dto.reflection.ReflectionAnswerView;
import com.kbv.education.dto.reflection.ReflectionQuestionResponse;
import com.kbv.education.dto.reflection.ReflectionResponse;
import com.kbv.education.dto.reflection.TodayReflectionResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.ReflectionAnswer;
import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.ReflectionQuestion;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.ReflectionAnswerRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.ReflectionQuestionRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.entity.enums.ActivityType;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.repository.spec.ReflectionEntrySpecifications;
import com.kbv.education.service.ActivityService;
import com.kbv.education.service.NotificationService;
import com.kbv.education.service.ReflectionQuestionService;
import com.kbv.education.service.ReflectionService;
import com.kbv.education.service.ai.ReflectionTranscriptionService;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.service.storage.StoredFile;
import com.kbv.education.service.storage.VirusScanner;
import com.kbv.education.utils.MimeTypes;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionServiceImpl implements ReflectionService {

    private static final String SUBDIR = "reflections";
    private static final Set<String> AUDIO_ALLOWED = Set.of("mp3", "wav", "m4a", "aac");
    private static final int MAX_AUDIO_MB = 25;
    private static final int PREVIEW_LEN = 140;
    private static final List<String> SORTABLE = List.of("submittedAt", "reflectionDate", "createdAt");

    private final ReflectionEntryRepository reflectionRepository;
    private final ReflectionAnswerRepository answerRepository;
    private final ReflectionQuestionRepository questionRepository;
    private final ReflectionQuestionService questionService;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final FileStorageService fileStorageService;
    private final VirusScanner virusScanner;
    private final ReflectionTranscriptionService transcriptionService;
    private final ActivityService activityService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // ========================= STUDENT =========================

    @Override
    @Transactional(readOnly = true)
    public TodayReflectionResponse getToday(UUID studentId) {
        LocalDate today = LocalDate.now();
        List<ReflectionQuestionResponse> questions = questionService.listEnabled();
        ReflectionResponse existing = reflectionRepository
                .findByStudent_IdAndReflectionDateAndDeletedFalse(studentId, today)
                .map(this::toResponse)
                .orElse(null);
        return new TodayReflectionResponse(today, questions, existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReflectionResponse> getMine(UUID studentId) {
        return reflectionRepository.findByStudent_IdAndDeletedFalseOrderByReflectionDateDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReflectionResponse getMineById(UUID studentId, UUID id) {
        ReflectionEntry entry = loadOwned(studentId, id);
        return toResponse(entry);
    }

    @Override
    @Transactional
    public ReflectionResponse submit(UUID studentId, String answersJson, MultipartFile audio) {
        User student = userRepository.findByIdAndDeletedFalse(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
        LocalDate today = LocalDate.now();

        if (reflectionRepository.existsByStudent_IdAndReflectionDateAndDeletedFalse(studentId, today)) {
            throw new BusinessRuleException("You have already submitted today's reflection. Edit it instead.");
        }

        List<ReflectionAnswerInput> inputs = parseAnswers(answersJson);

        ReflectionEntry entry = new ReflectionEntry();
        entry.setStudent(student);
        entry.setReflectionDate(today);
        ReflectionEntry saved = reflectionRepository.save(entry);

        boolean hasText = saveAnswers(saved, inputs);
        boolean hasAudio = attachAudio(saved, audio);
        saved.setReflectionType(computeType(hasText, hasAudio));
        reflectionRepository.save(saved);

        activityService.record(studentId, ActivityType.REFLECTION_SUBMITTED, "Reflection submitted",
                "Daily reflection", ReferenceType.REFLECTION, saved.getId(), today);

        notificationService.notifyAdmins(NotificationType.REFLECTION_SUBMITTED, "Reflection Submitted",
                student.getFullName() + " submitted today's reflection", ReferenceType.REFLECTION, saved.getId());

        log.info("Student {} submitted reflection for {}", studentId, today);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ReflectionResponse update(UUID studentId, UUID id, String answersJson, MultipartFile audio,
                                     boolean removeAudio) {
        ReflectionEntry entry = loadOwned(studentId, id);
        if (!isEditable(entry)) {
            throw new BusinessRuleException("This reflection can no longer be edited (only until midnight of its day).");
        }

        List<ReflectionAnswerInput> inputs = parseAnswers(answersJson);
        boolean hasText = saveAnswers(entry, inputs);

        if (removeAudio) {
            deleteAudio(entry);
        }
        boolean addedAudio = attachAudio(entry, audio);
        boolean hasAudio = addedAudio || entry.getAudioStoredName() != null;

        entry.setReflectionType(computeType(hasText, hasAudio));
        reflectionRepository.save(entry);
        return toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult downloadMyAudio(UUID studentId, UUID id) {
        ReflectionEntry entry = loadOwned(studentId, id);
        return buildAudioDownload(entry);
    }

    // ========================= ADMIN =========================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReflectionSummary> adminList(UUID cohortId, UUID studentId, ReflectionType type,
                                                          String search, int page, int size, String sort,
                                                          String direction) {
        Specification<ReflectionEntry> spec = Specification.where(ReflectionEntrySpecifications.notDeleted())
                .and(ReflectionEntrySpecifications.inCohort(cohortId))
                .and(ReflectionEntrySpecifications.forStudent(studentId))
                .and(ReflectionEntrySpecifications.ofType(type))
                .and(ReflectionEntrySpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<ReflectionEntry> result = reflectionRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public ReflectionResponse adminGet(UUID id) {
        return toResponse(load(id));
    }

    @Override
    @Transactional
    public ReflectionResponse adminUpdateText(UUID id, List<ReflectionAnswerInput> answers) {
        ReflectionEntry entry = load(id);
        boolean hasText = saveAnswers(entry, answers == null ? List.of() : answers);
        entry.setReflectionType(computeType(hasText, entry.getAudioStoredName() != null));
        reflectionRepository.save(entry);
        return toResponse(entry);
    }

    @Override
    @Transactional
    public void adminDelete(UUID id) {
        ReflectionEntry entry = load(id);
        entry.setDeleted(true);
        reflectionRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult adminDownloadAudio(UUID id) {
        return buildAudioDownload(load(id));
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResult export(UUID id) {
        ReflectionEntry entry = load(id);
        User student = entry.getStudent();

        StringBuilder sb = new StringBuilder();
        sb.append("KBV Education — Reflection\n");
        sb.append("Student: ").append(student.getFullName()).append('\n');
        sb.append("Date: ").append(entry.getReflectionDate()).append('\n');
        sb.append("Type: ").append(entry.getReflectionType()).append('\n');
        if (entry.getAudioFileName() != null) {
            sb.append("Audio: ").append(entry.getAudioFileName()).append('\n');
        }
        sb.append("\n");
        for (ReflectionAnswerView a : loadAnswerViews(entry)) {
            sb.append("Q: ").append(a.questionText()).append('\n');
            sb.append("A: ").append(a.answerText() == null ? "" : a.answerText()).append("\n\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(bytes);
        String fileName = "reflection-" + entry.getReflectionDate() + "-"
                + student.getFullName().replaceAll("\\s+", "_") + ".txt";
        return new FileDownloadResult(fileName, "text/plain; charset=UTF-8", bytes.length, resource);
    }

    // ========================= helpers =========================

    private ReflectionEntry load(UUID id) {
        return reflectionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Reflection", id));
    }

    private ReflectionEntry loadOwned(UUID studentId, UUID id) {
        ReflectionEntry entry = load(id);
        if (!entry.getStudent().getId().equals(studentId)) {
            throw ResourceNotFoundException.of("Reflection", id);
        }
        return entry;
    }

    private boolean isEditable(ReflectionEntry entry) {
        return entry.getReflectionDate().isEqual(LocalDate.now());
    }

    private List<ReflectionAnswerInput> parseAnswers(String answersJson) {
        if (answersJson == null || answersJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(answersJson, new TypeReference<List<ReflectionAnswerInput>>() {
            });
        } catch (Exception e) {
            throw new BadRequestException("Invalid answers payload");
        }
    }

    /** Replaces the entry's answers with the provided ones. Returns true if any non-blank answer was saved. */
    private boolean saveAnswers(ReflectionEntry entry, List<ReflectionAnswerInput> inputs) {
        // Soft-delete existing answers.
        List<ReflectionAnswer> existing = answerRepository.findByReflectionEntry_IdAndDeletedFalse(entry.getId());
        for (ReflectionAnswer a : existing) {
            a.setDeleted(true);
        }
        answerRepository.saveAll(existing);

        boolean hasText = false;
        for (ReflectionAnswerInput input : inputs) {
            if (input.questionId() == null || input.answerText() == null || input.answerText().isBlank()) {
                continue;
            }
            ReflectionQuestion question = questionRepository.findByIdAndDeletedFalse(input.questionId())
                    .orElseThrow(() -> new BadRequestException("Unknown reflection question: " + input.questionId()));
            ReflectionAnswer answer = new ReflectionAnswer();
            answer.setReflectionEntry(entry);
            answer.setQuestion(question);
            answer.setAnswerText(input.answerText().trim());
            answerRepository.save(answer);
            hasText = true;
        }
        return hasText;
    }

    /** Stores the audio (if provided) and updates the entry. Returns true if audio was attached. */
    private boolean attachAudio(ReflectionEntry entry, MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            return false;
        }
        String original = StringUtils.cleanPath(
                audio.getOriginalFilename() == null ? "audio" : audio.getOriginalFilename());
        String ext = extensionOf(original);
        if (!AUDIO_ALLOWED.contains(ext)) {
            throw new BadRequestException("Audio type '" + ext + "' is not allowed. Allowed: " + AUDIO_ALLOWED);
        }
        if (audio.getSize() > (long) MAX_AUDIO_MB * 1024 * 1024) {
            throw new BadRequestException("Audio exceeds the maximum size of " + MAX_AUDIO_MB + " MB");
        }
        virusScanner.scan(audio);

        // Replace any previous audio.
        deleteAudio(entry);

        StoredFile stored = fileStorageService.store(audio, SUBDIR);
        entry.setAudioFileName(original);
        entry.setAudioStoredName(stored.storedName());
        entry.setAudioFileType(ext);
        entry.setAudioFileSize(stored.size());

        // Extension point — no-op under the manual implementation.
        String transcript = transcriptionService.transcribe(entry.getId(), SUBDIR, stored.storedName());
        if (transcript != null) {
            entry.setTranscript(transcript);
        }
        return true;
    }

    private void deleteAudio(ReflectionEntry entry) {
        if (entry.getAudioStoredName() != null) {
            fileStorageService.delete(SUBDIR, entry.getAudioStoredName());
            entry.setAudioFileName(null);
            entry.setAudioStoredName(null);
            entry.setAudioFileType(null);
            entry.setAudioFileSize(null);
            entry.setTranscript(null);
        }
    }

    private FileDownloadResult buildAudioDownload(ReflectionEntry entry) {
        if (entry.getAudioStoredName() == null) {
            throw ResourceNotFoundException.of("Audio", entry.getId());
        }
        Resource resource = fileStorageService.loadAsResource(SUBDIR, entry.getAudioStoredName());
        return new FileDownloadResult(
                entry.getAudioFileName(),
                MimeTypes.forExtension(entry.getAudioFileType()),
                entry.getAudioFileSize() == null ? 0 : entry.getAudioFileSize(),
                resource);
    }

    private ReflectionType computeType(boolean hasText, boolean hasAudio) {
        if (hasText && hasAudio) {
            return ReflectionType.BOTH;
        }
        if (hasAudio) {
            return ReflectionType.VOICE;
        }
        return ReflectionType.TYPED;
    }

    private String extensionOf(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }

    private List<ReflectionAnswerView> loadAnswerViews(ReflectionEntry entry) {
        List<ReflectionAnswerView> views = new ArrayList<>();
        for (ReflectionAnswer a : answerRepository.findByReflectionEntry_IdAndDeletedFalse(entry.getId())) {
            views.add(new ReflectionAnswerView(
                    a.getQuestion().getId(),
                    a.getQuestion().getQuestionText(),
                    a.getAnswerText()));
        }
        return views;
    }

    private String cohortNameOf(UUID studentId) {
        return studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .map(sc -> sc.getCohort().getName())
                .orElse(null);
    }

    private ReflectionResponse toResponse(ReflectionEntry entry) {
        User student = entry.getStudent();
        return new ReflectionResponse(
                entry.getId(),
                student.getId(),
                student.getFullName(),
                cohortNameOf(student.getId()),
                entry.getReflectionDate(),
                entry.getReflectionType(),
                entry.getSubmittedAt(),
                isEditable(entry),
                entry.getAudioStoredName() != null,
                entry.getAudioFileName(),
                loadAnswerViews(entry));
    }

    private AdminReflectionSummary toSummary(ReflectionEntry entry) {
        User student = entry.getStudent();
        return new AdminReflectionSummary(
                entry.getId(),
                student.getId(),
                student.getFullName(),
                cohortNameOf(student.getId()),
                entry.getReflectionDate(),
                entry.getSubmittedAt(),
                entry.getReflectionType(),
                buildPreview(entry),
                entry.getAudioStoredName() != null);
    }

    private String buildPreview(ReflectionEntry entry) {
        StringBuilder sb = new StringBuilder();
        for (ReflectionAnswer a : answerRepository.findByReflectionEntry_IdAndDeletedFalse(entry.getId())) {
            if (a.getAnswerText() != null && !a.getAnswerText().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append(a.getAnswerText().trim());
            }
            if (sb.length() >= PREVIEW_LEN) {
                break;
            }
        }
        String text = sb.toString();
        return text.length() > PREVIEW_LEN ? text.substring(0, PREVIEW_LEN) + "…" : text;
    }
}
