package com.kbv.education.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.entity.AiBatch;
import com.kbv.education.entity.AiJob;
import com.kbv.education.entity.AiResult;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.ReflectionAnswer;
import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.enums.AiBatchStatus;
import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.entity.enums.TranscriptStatus;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.AiBatchRepository;
import com.kbv.education.repository.AiJobRepository;
import com.kbv.education.repository.AiResultRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.ReflectionAnswerRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.AuditLogService;
import com.kbv.education.service.ai.provider.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Every database step of the AI engine, each in its own short transaction, so no
 * transaction is ever held open while waiting for an AI to answer.
 */
@Component
@RequiredArgsConstructor
public class AiJobStore {

    /** Actor recorded in the Audit Trail for everything the AI does. */
    public static final String AI_ACTOR = "AI";

    private final AiJobRepository jobRepository;
    private final AiBatchRepository batchRepository;
    private final AiResultRepository resultRepository;
    private final PracticeSessionRepository practiceRepository;
    private final ReflectionEntryRepository reflectionRepository;
    private final ReflectionAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------ targets

    @Transactional(readOnly = true)
    public List<UUID> newPracticeTargets(Instant from) {
        return practiceRepository.findIdsNotYetSentToAi(from);
    }

    @Transactional(readOnly = true)
    public List<UUID> newReflectionTargets(Instant from) {
        return reflectionRepository.findIdsNotYetSentToAi(from);
    }

    @Transactional(readOnly = true)
    public List<UUID> voiceNotesToTranscribe(Instant from) {
        return reflectionRepository.findIdsAwaitingTranscription(from);
    }

    @Transactional(readOnly = true)
    public PiiRedactor redactor() {
        return PiiRedactor.forUsers(userRepository.findByDeletedFalse());
    }

    // ------------------------------------------------------------ what the AI reads

    public record PracticeData(UUID id, UUID studentId, String subject, StudyType studyType, int durationMinutes,
                               LocalDate studyDate, List<TranscriptTurns.Turn> turns, int chars) {
    }

    public record ReflectionData(UUID id, UUID studentId, LocalDate date, String typedAnswers, String transcript,
                                 TranscriptStatus transcriptStatus, String audioStoredName, String audioFileType) {
    }

    /** Loads a session for the AI, splitting its transcript into turns first if that was never done. */
    @Transactional
    public PracticeData loadPractice(UUID id) {
        PracticeSession s = practiceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Practice session", id));
        if (s.getTurnSplitMethod() == null) {
            applyTurns(s);
            practiceRepository.save(s);
        }
        return new PracticeData(s.getId(), s.getStudent().getId(), s.getSubject(), s.getStudyType(),
                s.getDurationMinutes(), s.getStudyDate(), readTurns(s.getTranscriptTurns()),
                s.getTranscriptChars() == null ? 0 : s.getTranscriptChars());
    }

    /** Splits the transcript into numbered turns and records its length. The transcript itself is untouched. */
    public void applyTurns(PracticeSession s) {
        TranscriptTurns.Split split = TranscriptTurns.split(s.getTranscript());
        try {
            s.setTranscriptTurns(objectMapper.writeValueAsString(split.turns()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store transcript turns", e);
        }
        s.setTurnCount(split.turns().size());
        s.setTranscriptChars(s.getTranscript() == null ? 0 : s.getTranscript().length());
        s.setTurnSplitMethod(split.method());
    }

    public List<TranscriptTurns.Turn> readTurns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TranscriptTurns.Turn>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public ReflectionData loadReflection(UUID id) {
        ReflectionEntry r = reflectionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Reflection", id));
        StringBuilder typed = new StringBuilder();
        for (ReflectionAnswer a : answerRepository.findByReflectionEntry_IdAndDeletedFalse(r.getId())) {
            if (a.getAnswerText() != null && !a.getAnswerText().isBlank()) {
                typed.append("Q: ").append(a.getQuestion().getQuestionText()).append('\n')
                        .append("A: ").append(a.getAnswerText().strip()).append("\n\n");
            }
        }
        return new ReflectionData(r.getId(), r.getStudent().getId(), r.getReflectionDate(), typed.toString().strip(),
                r.getTranscript(), r.getTranscriptStatus(), r.getAudioStoredName(), r.getAudioFileType());
    }

    /** Class median and this student's median, for one study type. Counted by the app, not the AI. */
    public record LengthStats(Integer classMedianTurns, Integer classMedianChars,
                              Integer studentMedianTurns, Integer studentMedianChars, int classSessions,
                              int studentSessions) {
    }

    @Transactional(readOnly = true)
    public LengthStats lengthStats(StudyType studyType, UUID studentId) {
        List<Integer> classTurns = new ArrayList<>();
        List<Integer> classChars = new ArrayList<>();
        List<Integer> studentTurns = new ArrayList<>();
        List<Integer> studentChars = new ArrayList<>();
        for (Object[] row : practiceRepository.lengthsForStudyType(studyType)) {
            int turns = ((Number) row[1]).intValue();
            int chars = row[2] == null ? 0 : ((Number) row[2]).intValue();
            classTurns.add(turns);
            classChars.add(chars);
            if (studentId.equals(row[0])) {
                studentTurns.add(turns);
                studentChars.add(chars);
            }
        }
        return new LengthStats(median(classTurns), median(classChars), median(studentTurns), median(studentChars),
                classTurns.size(), studentTurns.size());
    }

    private static Integer median(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    // ------------------------------------------------------------ jobs

    @Transactional
    public AiJob createJob(AiJobType type, UUID targetId, AiProvider provider, String model, UUID packVersionId) {
        // Any earlier result for this target stays saved but is no longer the current one.
        resultRepository.markNotCurrent(targetId);
        AiJob job = new AiJob();
        job.setJobType(type);
        job.setTargetId(targetId);
        job.setProvider(provider);
        job.setModel(model);
        job.setReferencePackVersionId(packVersionId);
        job.setStatus(AiJobStatus.QUEUED);
        AiJob saved = jobRepository.save(job);
        setTargetStatus(type, targetId, AiJobStatus.QUEUED);
        return saved;
    }

    @Transactional(readOnly = true)
    public AiJob job(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> ResourceNotFoundException.of("AI job", jobId));
    }

    @Transactional
    public void assignProvider(UUID jobId, AiProvider provider, String model, boolean backup) {
        AiJob job = job(jobId);
        job.setProvider(provider);
        job.setModel(model);
        job.setBackupUsed(job.isBackupUsed() || backup);
        jobRepository.save(job);
    }

    @Transactional
    public void countFormatRetry(UUID jobId) {
        AiJob job = job(jobId);
        job.setFormatRetries(job.getFormatRetries() + 1);
        jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public boolean hasResult(UUID jobId) {
        return resultRepository.existsByJobId(jobId);
    }

    @Transactional
    public AiResult saveSuccess(UUID jobId, AiResultValidator.Validated validated, AiResponse response,
                                String rawResponse, boolean viaBatch) {
        AiJob job = job(jobId);
        resultRepository.markNotCurrent(job.getTargetId());

        AiResult result = new AiResult();
        result.setJobId(job.getId());
        result.setJobType(job.getJobType());
        result.setTargetId(job.getTargetId());
        result.setProvider(job.getProvider());
        result.setModel(response.model() == null ? job.getModel() : response.model());
        result.setReferencePackVersionId(job.getReferencePackVersionId());
        result.setRawResponse(rawResponse);
        result.setResultJson(validated.json().toString());
        result.setRecommendation(validated.recommendation());
        result.setTrustRating(validated.trustRating());
        result.setEngagementRating(validated.engagementRating());
        result.setFlagCount(validated.flagCount());
        result.setNeedsAttention(validated.needsAttention());
        result.setInputTokens(response.inputTokens());
        result.setOutputTokens(response.outputTokens());
        result.setCacheReadTokens(response.cacheReadTokens());
        result.setCacheWriteTokens(response.cacheWriteTokens());
        result.setViaBatch(viaBatch);
        result.setCurrent(true);
        AiResult saved = resultRepository.save(result);

        job.setStatus(AiJobStatus.SUCCEEDED);
        job.setCompletedAt(Instant.now());
        job.setFailureReason(null);
        job.setFailureDetail(null);
        jobRepository.save(job);

        if (job.getJobType() == AiJobType.PRACTICE_REVIEW) {
            practiceRepository.findById(job.getTargetId()).ifPresent(s -> {
                s.setAiStatus(AiJobStatus.SUCCEEDED);
                s.setAiTrustRating(validated.trustRating());
                s.setAiRecommendation(validated.recommendation());
                s.setAiFlagCount(validated.flagCount());
                practiceRepository.save(s);
            });
            audit("AI_PRACTICE_ANALYSED", "PRACTICE_SESSION", job.getTargetId(),
                    "Analysed by " + job.getProvider() + " (" + result.getModel() + "): " + validated.recommendation()
                            + ", trust " + validated.trustRating() + ", flags " + validated.flagCount());
        } else {
            reflectionRepository.findById(job.getTargetId()).ifPresent(r -> {
                r.setAiStatus(AiJobStatus.SUCCEEDED);
                r.setAiFlagCount(validated.flagCount());
                r.setAiNeedsAttention(validated.needsAttention() || r.getTranscriptStatus() == TranscriptStatus.FAILED);
                reflectionRepository.save(r);
            });
            audit("AI_REFLECTION_ANALYSED", "REFLECTION", job.getTargetId(),
                    "Analysed by " + job.getProvider() + " (" + result.getModel() + "): flags "
                            + validated.flagCount() + ", needs attention " + validated.needsAttention());
        }
        return saved;
    }

    /** Both AIs failed (or none could take the job): the session stays "Waiting for approval", shown "not analysed". */
    @Transactional
    public void markNotAnalysed(UUID jobId, AiFailureReason reason, String detail) {
        AiJob job = job(jobId);
        job.setStatus(AiJobStatus.NOT_ANALYSED);
        job.setFailureReason(reason);
        job.setFailureDetail(detail);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
        if (job.getJobType() == AiJobType.PRACTICE_REVIEW) {
            setTargetStatus(AiJobType.PRACTICE_REVIEW, job.getTargetId(), AiJobStatus.NOT_ANALYSED);
            audit("AI_NOT_ANALYSED", "PRACTICE_SESSION", job.getTargetId(), reason + ": " + detail);
        } else {
            reflectionRepository.findById(job.getTargetId()).ifPresent(r -> {
                r.setAiStatus(AiJobStatus.NOT_ANALYSED);
                r.setAiNeedsAttention(true);
                reflectionRepository.save(r);
            });
            audit("AI_NOT_ANALYSED", "REFLECTION", job.getTargetId(), reason + ": " + detail);
        }
    }

    private void setTargetStatus(AiJobType type, UUID targetId, AiJobStatus status) {
        if (type == AiJobType.PRACTICE_REVIEW) {
            practiceRepository.findById(targetId).ifPresent(s -> {
                s.setAiStatus(status);
                if (status != AiJobStatus.SUCCEEDED) {
                    s.setAiTrustRating(null);
                    s.setAiRecommendation(null);
                    s.setAiFlagCount(null);
                }
                practiceRepository.save(s);
            });
        } else {
            reflectionRepository.findById(targetId).ifPresent(r -> {
                r.setAiStatus(status);
                reflectionRepository.save(r);
            });
        }
    }

    // ------------------------------------------------------------ batches

    @Transactional
    public AiBatch saveBatch(AiProvider provider, String providerBatchId, Instant deadline, Collection<UUID> jobIds) {
        AiBatch batch = new AiBatch();
        batch.setProvider(provider);
        batch.setProviderBatchId(providerBatchId);
        batch.setSubmittedAt(Instant.now());
        batch.setDeadlineAt(deadline);
        batch.setRequestCount(jobIds.size());
        AiBatch saved = batchRepository.save(batch);
        for (UUID jobId : jobIds) {
            AiJob job = job(jobId);
            job.setBatchId(saved.getId());
            job.setStatus(AiJobStatus.SUBMITTED);
            jobRepository.save(job);
            setTargetStatus(job.getJobType(), job.getTargetId(), AiJobStatus.SUBMITTED);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AiBatch> openBatches() {
        return batchRepository.findByStatus(AiBatchStatus.SUBMITTED);
    }

    @Transactional(readOnly = true)
    public List<AiJob> waitingJobs(UUID batchId) {
        return jobRepository.findByBatchIdAndStatus(batchId, AiJobStatus.SUBMITTED);
    }

    @Transactional
    public void closeBatch(UUID batchId, AiBatchStatus status) {
        batchRepository.findById(batchId).ifPresent(b -> {
            b.setStatus(status);
            b.setEndedAt(Instant.now());
            batchRepository.save(b);
        });
    }

    // ------------------------------------------------------------ voice notes

    @Transactional
    public void saveTranscript(UUID reflectionId, String text, String model) {
        reflectionRepository.findById(reflectionId).ifPresent(r -> {
            r.setTranscript(text);
            r.setTranscriptStatus(TranscriptStatus.DONE);
            r.setTranscriptModel(model);
            r.setTranscribedAt(Instant.now());
            reflectionRepository.save(r);
        });
        audit("AI_VOICE_NOTE_TRANSCRIBED", "REFLECTION", reflectionId, "Transcribed by Gemini (" + model + ")");
    }

    /** Transcription failed or came back empty: the reflection "needs my attention"; the audio stays playable. */
    @Transactional
    public void markTranscriptFailed(UUID reflectionId, String detail) {
        reflectionRepository.findById(reflectionId).ifPresent(r -> {
            r.setTranscriptStatus(TranscriptStatus.FAILED);
            r.setAiNeedsAttention(true);
            reflectionRepository.save(r);
        });
        audit("AI_TRANSCRIPTION_FAILED", "REFLECTION", reflectionId, detail);
    }

    private void audit(String action, String entityType, UUID entityId, String detail) {
        auditLogService.record(action, entityType, entityId, AI_ACTOR, null, truncate(detail), null, null);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
    }
}
