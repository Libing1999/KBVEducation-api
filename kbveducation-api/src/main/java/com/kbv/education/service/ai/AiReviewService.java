package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbv.education.dto.ai.AiPracticeCardResponse;
import com.kbv.education.dto.ai.AiReflectionCardResponse;
import com.kbv.education.dto.ai.AiStatusResponse;
import com.kbv.education.entity.AiJob;
import com.kbv.education.entity.AiResult;
import com.kbv.education.entity.AiSettings;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.enums.AiBatchStatus;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.AiBatchRepository;
import com.kbv.education.repository.AiJobRepository;
import com.kbv.education.repository.AiReferencePackVersionRepository;
import com.kbv.education.repository.AiResultRepository;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.ReflectionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only admin views of the AI's work: the practice review card, reflection notes, status. */
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final PracticeSessionRepository practiceRepository;
    private final ReflectionEntryRepository reflectionRepository;
    private final AiJobRepository jobRepository;
    private final AiResultRepository resultRepository;
    private final AiBatchRepository batchRepository;
    private final AiReferencePackVersionRepository packVersionRepository;
    private final AiJobStore store;
    private final AiSettingsService settingsService;
    private final AiEngine engine;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AiPracticeCardResponse practiceCard(UUID sessionId) {
        PracticeSession s = practiceRepository.findByIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Practice session", sessionId));
        Optional<AiJob> job = jobRepository.findFirstByTargetIdOrderByCreatedAtDesc(sessionId);
        Optional<AiResult> result = resultRepository.findFirstByTargetIdAndCurrentTrueOrderByCreatedAtDesc(sessionId);

        AiJobStore.LengthStats stats = store.lengthStats(s.getStudyType(), s.getStudent().getId());
        AiPracticeCardResponse.Length length = new AiPracticeCardResponse.Length(
                s.getStudyType(), s.getTranscriptChars(), s.getTurnCount(),
                stats.classMedianChars(), stats.classMedianTurns(),
                stats.studentSessions() < 2 ? null : stats.studentMedianChars(),
                stats.studentSessions() < 2 ? null : stats.studentMedianTurns());

        List<AiPracticeCardResponse.Turn> turns = new ArrayList<>();
        for (TranscriptTurns.Turn t : store.readTurns(s.getTranscriptTurns())) {
            turns.add(new AiPracticeCardResponse.Turn(t.n(), t.speaker(), t.text()));
        }

        return new AiPracticeCardResponse(
                sessionId,
                s.getAiStatus(),
                result.map(AiResult::getProvider).orElse(job.map(AiJob::getProvider).orElse(null)),
                result.map(AiResult::getModel).orElse(job.map(AiJob::getModel).orElse(null)),
                job.map(AiJob::isBackupUsed).orElse(false),
                packVersionNo(result.map(AiResult::getReferencePackVersionId)
                        .orElse(job.map(AiJob::getReferencePackVersionId).orElse(null))),
                result.map(AiResult::isViaBatch).orElse(false),
                result.map(AiResult::getCreatedAt).orElse(null),
                job.filter(j -> j.getStatus() == AiJobStatus.NOT_ANALYSED).map(AiJob::getFailureReason).orElse(null),
                job.filter(j -> j.getStatus() == AiJobStatus.NOT_ANALYSED).map(AiJob::getFailureDetail).orElse(null),
                result.map(r -> readJson(r.getResultJson())).orElse(null),
                length,
                s.getTurnSplitMethod(),
                turns,
                s.getAiOverrideReason());
    }

    @Transactional(readOnly = true)
    public AiReflectionCardResponse reflectionCard(UUID reflectionId) {
        ReflectionEntry r = reflectionRepository.findByIdAndDeletedFalse(reflectionId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reflection", reflectionId));
        Optional<AiJob> job = jobRepository.findFirstByTargetIdOrderByCreatedAtDesc(reflectionId);
        Optional<AiResult> result = resultRepository.findFirstByTargetIdAndCurrentTrueOrderByCreatedAtDesc(reflectionId);
        return new AiReflectionCardResponse(
                reflectionId,
                r.getAiStatus(),
                result.map(AiResult::getProvider).orElse(job.map(AiJob::getProvider).orElse(null)),
                result.map(AiResult::getModel).orElse(job.map(AiJob::getModel).orElse(null)),
                job.map(AiJob::isBackupUsed).orElse(false),
                packVersionNo(result.map(AiResult::getReferencePackVersionId).orElse(null)),
                result.map(AiResult::getCreatedAt).orElse(null),
                job.filter(j -> j.getStatus() == AiJobStatus.NOT_ANALYSED).map(AiJob::getFailureReason).orElse(null),
                job.filter(j -> j.getStatus() == AiJobStatus.NOT_ANALYSED).map(AiJob::getFailureDetail).orElse(null),
                result.map(x -> readJson(x.getResultJson())).orElse(null),
                r.isAiNeedsAttention(),
                r.getTranscriptStatus(),
                r.getTranscript(),
                r.getTranscriptModel(),
                r.getTranscribedAt());
    }

    @Transactional(readOnly = true)
    public AiStatusResponse status() {
        AiSettings settings = settingsService.getActive();
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<AiStatusResponse.Usage> usage = new ArrayList<>();
        for (AiProvider provider : AiProvider.values()) {
            for (Object[] row : resultRepository.usageSince(monthStart, provider)) {
                usage.add(new AiStatusResponse.Usage(provider, ((Number) row[4]).longValue(),
                        ((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(), ((Number) row[3]).longValue()));
            }
        }
        var configured = settingsService.toResponse(settings);
        return new AiStatusResponse(
                settings.isAiEnabled(),
                configured.claudeConfigured(),
                configured.geminiConfigured(),
                engine.isBusy(),
                settings.getLastRunDate(),
                batchRepository.countByStatus(AiBatchStatus.SUBMITTED),
                jobRepository.countByStatusIn(List.of(AiJobStatus.QUEUED, AiJobStatus.SUBMITTED)),
                jobRepository.countByStatusAndCompletedAtAfter(AiJobStatus.NOT_ANALYSED, monthStart),
                usage);
    }

    private Integer packVersionNo(UUID versionId) {
        if (versionId == null) {
            return null;
        }
        return packVersionRepository.findById(versionId).map(v -> v.getVersionNo()).orElse(null);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
