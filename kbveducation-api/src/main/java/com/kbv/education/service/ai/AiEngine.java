package com.kbv.education.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kbv.education.config.AiProperties;
import com.kbv.education.entity.AiBatch;
import com.kbv.education.entity.AiFieldDefinition;
import com.kbv.education.entity.AiJob;
import com.kbv.education.entity.AiSettings;
import com.kbv.education.entity.enums.AiBatchStatus;
import com.kbv.education.entity.enums.AiCallMode;
import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiJobType;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.entity.enums.TranscriptStatus;
import com.kbv.education.repository.AiFieldDefinitionRepository;
import com.kbv.education.service.PracticeService;
import com.kbv.education.service.ai.provider.AiBatchSubmitException;
import com.kbv.education.service.ai.provider.AiClient;
import com.kbv.education.service.ai.provider.AiRequest;
import com.kbv.education.service.ai.provider.AiResponse;
import com.kbv.education.service.ai.provider.ClaudeAiClient;
import com.kbv.education.service.ai.provider.GeminiAiClient;
import com.kbv.education.service.storage.FileStorageService;
import com.kbv.education.utils.MimeTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Runs the AI work (memos 12.10.2026 and 17.10.2026):
 *
 * <ol>
 *   <li>Every night at the configured time (Dubai by default), new voice notes are turned into
 *       text by Gemini (always a normal call), then every practice session and reflection
 *       submitted since the last run is sent to the AI chosen in Settings.</li>
 *   <li>In Batch mode the jobs go out as one batch per AI and are collected by
 *       {@link #pollBatches()}; in Normal mode each job is answered straight away.</li>
 *   <li>A job fails when the AI errors, the answer is empty / blocked / cut short / not in the
 *       fixed format (retried once first), the credit or spending limit runs out, or a batch
 *       misses its deadline. A failed job goes to the other AI (if backup is on and that AI is
 *       set up). If that fails too, the job is "not analysed".</li>
 * </ol>
 *
 * The app never stops working because of the AI: every failure ends in a saved state the
 * admin panel can show.
 */
@Slf4j
@Service
public class AiEngine {

    private static final List<String> SEVERITY = List.of("approve", "look", "reject_recommended");

    private final AiSettingsService settingsService;
    private final ReferencePackService referencePackService;
    private final AiJobStore store;
    private final ClaudeAiClient claude;
    private final GeminiAiClient gemini;
    private final AiResultValidator validator;
    private final AiProperties properties;
    private final PracticeService practiceService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor aiExecutor;
    private final AiFieldDefinitionRepository fieldRepository;

    /** Only one run or batch check at a time. */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    public AiEngine(AiSettingsService settingsService, ReferencePackService referencePackService, AiJobStore store,
                    ClaudeAiClient claude, GeminiAiClient gemini, AiResultValidator validator,
                    AiProperties properties, PracticeService practiceService,
                    FileStorageService fileStorageService, ObjectMapper objectMapper,
                    @Qualifier("aiExecutor") TaskExecutor aiExecutor,
                    AiFieldDefinitionRepository fieldRepository) {
        this.settingsService = settingsService;
        this.referencePackService = referencePackService;
        this.store = store;
        this.claude = claude;
        this.gemini = gemini;
        this.validator = validator;
        this.properties = properties;
        this.practiceService = practiceService;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
        this.aiExecutor = aiExecutor;
        this.fieldRepository = fieldRepository;
    }

    /** Everything one pass needs, read once. */
    private record RunContext(AiSettings settings, PiiRedactor redactor) {
    }

    /** What is sent for one job: one part normally, several for a very long transcript. */
    private record Payload(String instructions, Map<String, Object> schema, String referencePack,
                           List<String> parts, String sourceText, int turnCount,
                           List<AiFieldDefinition> customFields) {
    }

    private record Prepared(UUID jobId, UUID targetId, AiJobType type, AiProvider provider, Payload payload) {
    }

    private record Outcome(boolean ok, AiFailureReason failure, String detail) {
        static final Outcome SUCCESS = new Outcome(true, null, null);

        static Outcome failed(AiFailureReason reason, String detail) {
            return new Outcome(false, reason, detail);
        }
    }

    // =================================================================== entry points

    /** Called every minute by {@link AiScheduler}: collects finished batches and starts tonight's run when due. */
    public void tick() {
        if (!busy.compareAndSet(false, true)) {
            return;
        }
        try {
            pollBatches();
            AiSettings settings = settingsService.getActive();
            if (settings.isAiEnabled() && isDue(settings)) {
                settingsService.markRunDate(today(settings));
                runAll();
            }
        } catch (RuntimeException e) {
            log.error("AI tick failed: {}", e.getMessage(), e);
        } finally {
            busy.set(false);
        }
    }

    /** "Run now" from the admin panel: tonight's run, started immediately, in the background. */
    public boolean startRunNow() {
        if (busy.get()) {
            return false;
        }
        aiExecutor.execute(() -> {
            if (!busy.compareAndSet(false, true)) {
                return;
            }
            try {
                runAll();
                pollBatches();
            } catch (RuntimeException e) {
                log.error("AI run failed: {}", e.getMessage(), e);
            } finally {
                busy.set(false);
            }
        });
        return true;
    }

    /**
     * Analyse one session or reflection again, as a normal call (e.g. to fill in new fields or
     * after a pack change). The earlier result is kept but is no longer the current one.
     */
    public void startReanalyse(AiJobType type, UUID targetId) {
        aiExecutor.execute(() -> {
            try {
                AiSettings settings = settingsService.getActive();
                RunContext ctx = new RunContext(settings, store.redactor());
                ReferencePackService.ActivePack pack = referencePackService.activePack();
                if (type == AiJobType.REFLECTION_REVIEW) {
                    transcribe(ctx, targetId);
                }
                Prepared prepared = prepare(ctx, pack, type, targetId);
                if (prepared != null) {
                    process(ctx, prepared, null);
                }
            } catch (RuntimeException e) {
                log.error("Re-analysis of {} {} failed: {}", type, targetId, e.getMessage(), e);
            }
        });
    }

    /**
     * Analyse many older items again, one after another, as normal calls (memo 17.10.2026, 2.1:
     * "I can choose to run older sessions again to fill [a new field] in"). Runs in the background;
     * each item keeps its earlier results, which stop being the current ones.
     */
    public void startReanalyseMany(AiJobType type, List<UUID> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        aiExecutor.execute(() -> {
            AiSettings settings = settingsService.getActive();
            RunContext ctx = new RunContext(settings, store.redactor());
            ReferencePackService.ActivePack pack = referencePackService.activePack();
            int done = 0;
            for (UUID targetId : targetIds) {
                try {
                    if (type == AiJobType.REFLECTION_REVIEW) {
                        transcribe(ctx, targetId);
                    }
                    Prepared prepared = prepare(ctx, pack, type, targetId);
                    if (prepared != null) {
                        process(ctx, prepared, null);
                    }
                    done++;
                } catch (RuntimeException e) {
                    log.error("Re-analysis of {} {} failed: {}", type, targetId, e.getMessage(), e);
                }
            }
            log.info("Re-analysed {} of {} {} item(s)", done, targetIds.size(), type);
        });
    }

    public boolean isBusy() {
        return busy.get();
    }

    private boolean isDue(AiSettings settings) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(settings.getRunTimezone()));
        return !now.toLocalTime().isBefore(settings.getNightlyRunTime())
                && !now.toLocalDate().equals(settings.getLastRunDate());
    }

    private java.time.LocalDate today(AiSettings settings) {
        return ZonedDateTime.now(ZoneId.of(settings.getRunTimezone())).toLocalDate();
    }

    // =================================================================== the nightly run

    private void runAll() {
        AiSettings settings = settingsService.getActive();
        Instant from = settings.getAnalyseFrom() != null ? settings.getAnalyseFrom() : Instant.now();
        RunContext ctx = new RunContext(settings, store.redactor());
        ReferencePackService.ActivePack pack = referencePackService.activePack();

        // 1. Voice notes first, so tonight's reflections already have their text.
        for (UUID reflectionId : store.voiceNotesToTranscribe(from)) {
            transcribe(ctx, reflectionId);
        }

        // 2. Everything submitted since the last run.
        List<Prepared> prepared = new ArrayList<>();
        for (UUID id : store.newPracticeTargets(from)) {
            addIfPresent(prepared, prepare(ctx, pack, AiJobType.PRACTICE_REVIEW, id));
        }
        for (UUID id : store.newReflectionTargets(from)) {
            addIfPresent(prepared, prepare(ctx, pack, AiJobType.REFLECTION_REVIEW, id));
        }
        log.info("AI run: {} jobs ({} mode)", prepared.size(), settings.getCallMode());
        if (prepared.isEmpty()) {
            return;
        }

        if (settings.getCallMode() == AiCallMode.NORMAL) {
            for (Prepared p : prepared) {
                safely(() -> process(ctx, p, null), p);
            }
        } else {
            submitBatches(ctx, prepared);
        }
    }

    private static void addIfPresent(List<Prepared> list, Prepared p) {
        if (p != null) {
            list.add(p);
        }
    }

    private void safely(Runnable work, Prepared p) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.error("AI job {} for {} failed unexpectedly: {}", p.jobId(), p.targetId(), e.getMessage(), e);
            store.markNotAnalysed(p.jobId(), AiFailureReason.ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    /** Creates the job and builds what the AI will read. Returns null when there is nothing to analyse. */
    private Prepared prepare(RunContext ctx, ReferencePackService.ActivePack pack, AiJobType type, UUID targetId) {
        AiProvider provider = ctx.settings().providerFor(type);
        AiJob job = store.createJob(type, targetId, provider, ctx.settings().modelFor(provider), pack.versionId());
        Payload payload = buildPayload(ctx, pack.text(), type, targetId);
        if (payload == null) {
            store.markNotAnalysed(job.getId(), AiFailureReason.NO_CONTENT,
                    type == AiJobType.PRACTICE_REVIEW
                            ? "The session has no transcript to analyse"
                            : "The reflection has no text, and its voice note could not be turned into text");
            return null;
        }
        return new Prepared(job.getId(), targetId, type, provider, payload);
    }

    // =================================================================== voice notes

    /** Gemini turns the voice note into text, always as a normal call. Each voice note at most once. */
    private void transcribe(RunContext ctx, UUID reflectionId) {
        AiJobStore.ReflectionData data = store.loadReflection(reflectionId);
        if (data.audioStoredName() == null || data.transcriptStatus() != TranscriptStatus.NONE) {
            return;
        }
        if (!gemini.isConfigured()) {
            store.markTranscriptFailed(reflectionId, "Gemini is not set up, so the voice note was not turned into text");
            return;
        }
        byte[] audio;
        try (InputStream in = fileStorageService.loadAsResource("reflections", data.audioStoredName()).getInputStream()) {
            audio = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            store.markTranscriptFailed(reflectionId, "Audio file could not be read: " + e.getMessage());
            return;
        }
        String model = ctx.settings().getGeminiTranscribeModel();
        AiResponse response = gemini.transcribe(audio, MimeTypes.forExtension(data.audioFileType()), model);
        if (response.ok() && !response.text().isBlank()) {
            store.saveTranscript(reflectionId, response.text().strip(), response.model() == null ? model : response.model());
        } else {
            store.markTranscriptFailed(reflectionId, response.ok() ? "Transcript was empty" : response.detail());
        }
    }

    // =================================================================== building the request

    private Payload buildPayload(RunContext ctx, String packText, AiJobType type, UUID targetId) {
        return type == AiJobType.PRACTICE_REVIEW
                ? practicePayload(ctx, packText, targetId)
                : reflectionPayload(ctx, packText, targetId);
    }

    private Payload practicePayload(RunContext ctx, String packText, UUID id) {
        AiJobStore.PracticeData data = store.loadPractice(id);
        if (data.turns().isEmpty()) {
            return null;
        }
        PiiRedactor redactor = ctx.redactor();
        List<TranscriptTurns.Turn> turns = data.turns().stream()
                .map(t -> new TranscriptTurns.Turn(t.n(), t.speaker(), redactor.redact(t.text())))
                .toList();
        String lengthFacts = lengthFacts(data);
        String studyType = label(data.studyType());

        List<List<TranscriptTurns.Turn>> chunks = chunk(turns, properties.getMaxCharsPerRequest());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String note = chunks.size() == 1 ? null
                    : "This is part " + (i + 1) + " of " + chunks.size() + " of one long session. "
                    + "Judge only this part; turn numbers are the session's own.";
            parts.add(AiPrompts.practiceContent(data.subject(), studyType, data.durationMinutes(),
                    data.studyDate().toString(), lengthFacts, TranscriptTurns.numbered(chunks.get(i)), note));
        }
        String source = turns.stream().map(TranscriptTurns.Turn::text).collect(Collectors.joining("\n"));
        List<AiFieldDefinition> fields = customFields(AiJobType.PRACTICE_REVIEW);
        return new Payload(AiPrompts.PRACTICE_INSTRUCTIONS, AiPrompts.practiceSchema(fields), packText, parts, source,
                turns.size(), fields);
    }

    private Payload reflectionPayload(RunContext ctx, String packText, UUID id) {
        AiJobStore.ReflectionData data = store.loadReflection(id);
        String typed = ctx.redactor().redact(data.typedAnswers());
        String voice = data.transcriptStatus() == TranscriptStatus.DONE ? ctx.redactor().redact(data.transcript()) : null;
        if ((typed == null || typed.isBlank()) && (voice == null || voice.isBlank())) {
            return null;
        }
        String content = AiPrompts.reflectionContent(data.date().toString(), typed, voice);
        String source = (typed == null ? "" : typed) + "\n" + (voice == null ? "" : voice);
        List<AiFieldDefinition> fields = customFields(AiJobType.REFLECTION_REVIEW);
        return new Payload(AiPrompts.REFLECTION_INSTRUCTIONS, AiPrompts.reflectionSchema(fields), packText,
                List.of(content), source, 0, fields);
    }

    /** The admin's own fields for this kind of job, as they stand now (memo 17.10.2026, 2.1). */
    private List<AiFieldDefinition> customFields(AiJobType type) {
        return fieldRepository.findByJobTypeAndActiveTrueAndDeletedFalseOrderByDisplayOrderAscCreatedAtAsc(type);
    }

    /** Lengths are counted by the app, never by the AI (memo 12.10.2026, 4.2). */
    private String lengthFacts(AiJobStore.PracticeData data) {
        AiJobStore.LengthStats stats = store.lengthStats(data.studyType(), data.studentId());
        StringBuilder sb = new StringBuilder();
        sb.append("Session length: ").append(data.chars()).append(" characters, ")
                .append(data.turns().size()).append(" turns.\n");
        sb.append("Class median for this study type: ")
                .append(stats.classMedianTurns() == null ? "not enough sessions yet"
                        : stats.classMedianChars() + " characters, " + stats.classMedianTurns() + " turns")
                .append(".\n");
        sb.append("This student's median for this study type: ")
                .append(stats.studentSessions() < 2 ? "not enough sessions yet"
                        : stats.studentMedianChars() + " characters, " + stats.studentMedianTurns() + " turns")
                .append('.');
        return sb.toString();
    }

    private static String label(StudyType type) {
        return Arrays.stream(type.name().split("_"))
                .map(w -> w.charAt(0) + w.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    /** Splits turns into groups that each fit in one request, never cutting a turn in half. */
    private static List<List<TranscriptTurns.Turn>> chunk(List<TranscriptTurns.Turn> turns, int maxChars) {
        List<List<TranscriptTurns.Turn>> chunks = new ArrayList<>();
        List<TranscriptTurns.Turn> current = new ArrayList<>();
        int size = 0;
        for (TranscriptTurns.Turn t : turns) {
            int len = t.text().length() + 32;
            if (!current.isEmpty() && size + len > maxChars) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(t);
            size += len;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private AiRequest request(RunContext ctx, UUID jobId, AiProvider provider, Payload payload, int part) {
        String customId = payload.parts().size() == 1 ? jobId.toString() : jobId + "-p" + (part + 1);
        return new AiRequest(customId, payload.instructions(), payload.referencePack(), payload.parts().get(part),
                payload.schema(), ctx.settings().modelFor(provider), ctx.settings().getClaudeEffort());
    }

    private AiClient client(AiProvider provider) {
        return provider == AiProvider.CLAUDE ? claude : gemini;
    }

    // =================================================================== normal calls, retry and backup

    /**
     * Runs one job to the end: the chosen AI (with one retry for a bad answer), then the
     * other AI if backup is on and it is set up, else "not analysed".
     *
     * @param firstFailure set when the job already failed once on its first AI (e.g. in a batch)
     */
    private void process(RunContext ctx, Prepared p, AiResponse firstFailure) {
        Outcome outcome;
        if (firstFailure == null) {
            outcome = attempt(ctx, p, p.provider(), 2, false);
        } else if (firstFailure.failure().retryOnSameProvider()) {
            outcome = attempt(ctx, p, p.provider(), 1, false);
        } else {
            outcome = Outcome.failed(firstFailure.failure(), firstFailure.detail());
        }
        if (outcome.ok()) {
            return;
        }

        AiProvider backup = p.provider().other();
        // Never send to the other AI if the first one has, after all, already produced a result.
        if (ctx.settings().isBackupEnabled() && client(backup).isConfigured() && !store.hasResult(p.jobId())) {
            log.info("AI job {}: {} failed ({}), sending to {}", p.jobId(), p.provider(), outcome.failure(), backup);
            store.assignProvider(p.jobId(), backup, ctx.settings().modelFor(backup), true);
            String firstDetail = p.provider() + ": " + outcome.detail();
            outcome = attempt(ctx, p, backup, 2, true);
            if (outcome.ok()) {
                return;
            }
            outcome = Outcome.failed(outcome.failure(), firstDetail + " | " + backup + ": " + outcome.detail());
        }
        store.markNotAnalysed(p.jobId(), outcome.failure(), outcome.detail());
    }

    private Outcome attempt(RunContext ctx, Prepared p, AiProvider provider, int maxTries, boolean backup) {
        Outcome last = null;
        for (int i = 0; i < maxTries; i++) {
            last = callOnce(ctx, p, provider);
            if (last.ok() || !last.failure().retryOnSameProvider()) {
                return last;
            }
            if (last.failure() == AiFailureReason.INVALID_FORMAT) {
                store.countFormatRetry(p.jobId());
            }
        }
        return last;
    }

    private Outcome callOnce(RunContext ctx, Prepared p, AiProvider provider) {
        AiClient client = client(provider);
        if (!client.isConfigured()) {
            return Outcome.failed(AiFailureReason.NOT_CONFIGURED, provider + " is not set up (no API key)");
        }
        List<AiResultValidator.Validated> parts = new ArrayList<>();
        List<AiResponse> responses = new ArrayList<>();
        for (int i = 0; i < p.payload().parts().size(); i++) {
            AiResponse response = client.call(request(ctx, p.jobId(), provider, p.payload(), i));
            if (!response.ok()) {
                return Outcome.failed(response.failure(), response.detail());
            }
            try {
                parts.add(validator.validate(p.type(), response.text(), p.payload().sourceText(),
                        p.payload().turnCount(), p.payload().customFields()));
            } catch (AiResultValidator.InvalidResultException e) {
                return Outcome.failed(AiFailureReason.INVALID_FORMAT, e.getMessage());
            }
            responses.add(response);
        }
        AiResultValidator.Validated merged = parts.size() == 1 ? parts.get(0) : mergeParts(parts);
        String raw = responses.size() == 1 ? responses.get(0).text()
                : responses.stream().map(AiResponse::text).collect(Collectors.joining("\n---\n"));
        store.saveSuccess(p.jobId(), merged, sumUsage(responses), raw, false);
        afterSuccess(ctx, p, merged);
        return Outcome.SUCCESS;
    }

    /** Auto-approve (memo 12.10.2026, 4.3): off by default; never rejects. */
    private void afterSuccess(RunContext ctx, Prepared p, AiResultValidator.Validated v) {
        AiSettings s = ctx.settings();
        if (p.type() != AiJobType.PRACTICE_REVIEW || !s.isAutoApproveEnabled() || v.trustRating() == null) {
            return;
        }
        boolean eligible = v.trustRating() >= s.getAutoApproveMinRating()
                && (!s.isAutoApproveRequireNoFlags() || v.flagCount() == 0);
        if (eligible) {
            boolean spotCheck = ThreadLocalRandom.current().nextInt(100) < s.getSpotCheckPercent();
            practiceService.autoApprove(p.targetId(), spotCheck);
        }
    }

    private static AiResponse sumUsage(List<AiResponse> responses) {
        long in = 0;
        long out = 0;
        long read = 0;
        long write = 0;
        for (AiResponse r : responses) {
            in += r.inputTokens() == null ? 0 : r.inputTokens();
            out += r.outputTokens() == null ? 0 : r.outputTokens();
            read += r.cacheReadTokens() == null ? 0 : r.cacheReadTokens();
            write += r.cacheWriteTokens() == null ? 0 : r.cacheWriteTokens();
        }
        AiResponse first = responses.get(0);
        return AiResponse.success(first.text(), first.model(), in, out, read, write);
    }

    /** Combines the parts of a very long session into one result (memo 12.10.2026, 4.1). */
    private AiResultValidator.Validated mergeParts(List<AiResultValidator.Validated> parts) {
        ObjectNode merged = parts.get(0).json().deepCopy();
        String recommendation = "approve";
        int trust = 10;
        int engagementSum = 0;
        ObjectNode lowestEngagement = parts.get(0).json();
        String score = parts.get(0).json().path("past_paper_score").asText();
        Map<String, String> topics = new LinkedHashMap<>();
        Set<String> weak = new LinkedHashSet<>();
        ArrayNode flags = objectMapper.createArrayNode();
        for (AiResultValidator.Validated part : parts) {
            ObjectNode json = part.json();
            if (SEVERITY.indexOf(part.recommendation()) > SEVERITY.indexOf(recommendation)) {
                recommendation = part.recommendation();
            }
            trust = Math.min(trust, part.trustRating());
            engagementSum += part.engagementRating();
            if (part.engagementRating() < lowestEngagement.path("engagement_rating").asInt()) {
                lowestEngagement = json;
            }
            String partScore = json.path("past_paper_score").asText();
            if (!AiPrompts.SCORE_NOT_FOUND.equals(partScore) && !AiPrompts.SCORE_NOT_APPLICABLE.equals(partScore)) {
                score = partScore; // the final score comes at the end of the session
            }
            for (JsonNode t : json.path("topics")) {
                topics.merge(t.path("topic").asText(), t.path("depth").asText(),
                        (a, b) -> AiPrompts.DEPTHS.indexOf(b) > AiPrompts.DEPTHS.indexOf(a) ? b : a);
            }
            json.path("weak_topics").forEach(w -> weak.add(w.asText()));
            json.path("flags").forEach(flags::add);
        }
        int engagement = Math.round((float) engagementSum / parts.size());
        merged.put("recommendation", recommendation);
        merged.put("trust_rating", trust);
        merged.put("past_paper_score", score);
        merged.put("engagement_rating", engagement);
        merged.put("engagement_reason", lowestEngagement.path("engagement_reason").asText());
        merged.put("engagement_quote", lowestEngagement.path("engagement_quote").asText());
        merged.put("engagement_quote_verified", lowestEngagement.path("engagement_quote_verified").asBoolean());
        ArrayNode topicArray = merged.putArray("topics");
        topics.forEach((topic, depth) -> topicArray.addObject().put("topic", topic).put("depth", depth));
        ArrayNode weakArray = merged.putArray("weak_topics");
        weak.forEach(weakArray::add);
        merged.set("flags", flags);
        merged.put("analysed_in_parts", parts.size());
        return new AiResultValidator.Validated(merged, recommendation, trust, engagement, flags.size(), false);
    }

    // =================================================================== batches

    private void submitBatches(RunContext ctx, List<Prepared> prepared) {
        Map<AiProvider, List<Prepared>> byProvider = new EnumMap<>(AiProvider.class);
        for (Prepared p : prepared) {
            if (p.payload().parts().size() > 1) {
                // A session too long for one request goes as normal calls, part by part.
                safely(() -> process(ctx, p, null), p);
            } else {
                byProvider.computeIfAbsent(p.provider(), k -> new ArrayList<>()).add(p);
            }
        }
        byProvider.forEach((provider, jobs) -> {
            AiClient client = client(provider);
            List<AiRequest> requests = jobs.stream().map(p -> request(ctx, p.jobId(), provider, p.payload(), 0)).toList();
            try {
                String batchId = client.submitBatch(requests);
                store.saveBatch(provider, batchId, Instant.now().plus(client.batchDeadline()),
                        jobs.stream().map(Prepared::jobId).toList());
            } catch (AiBatchSubmitException e) {
                log.warn("{} batch could not be sent ({}): {}", provider, e.getReason(), e.getMessage());
                AiResponse failure = AiResponse.failed(e.getReason(), e.getMessage());
                for (Prepared p : jobs) {
                    safely(() -> process(ctx, p, failure), p);
                }
            }
        });
    }

    /** Collects finished batches and moves late or failed ones to the backup. */
    public void pollBatches() {
        List<AiBatch> open = store.openBatches();
        if (open.isEmpty()) {
            return;
        }
        RunContext ctx = new RunContext(settingsService.getActive(), store.redactor());
        for (AiBatch batch : open) {
            try {
                pollBatch(ctx, batch);
            } catch (RuntimeException e) {
                log.error("Checking {} batch {} failed: {}", batch.getProvider(), batch.getProviderBatchId(),
                        e.getMessage(), e);
            }
        }
    }

    private void pollBatch(RunContext ctx, AiBatch batch) {
        AiClient client = client(batch.getProvider());
        if (!client.isConfigured()) {
            failWaitingJobs(ctx, batch, AiBatchStatus.FAILED,
                    AiResponse.failed(AiFailureReason.NOT_CONFIGURED, batch.getProvider() + " is no longer set up"));
            return;
        }
        AiClient.BatchState state = client.checkBatch(batch.getProviderBatchId());
        if (state == AiClient.BatchState.RUNNING) {
            if (Instant.now().isAfter(batch.getDeadlineAt())) {
                client.cancelBatch(batch.getProviderBatchId());
                failWaitingJobs(ctx, batch, AiBatchStatus.EXPIRED, AiResponse.failed(AiFailureReason.EXPIRED,
                        "Batch was not finished within " + client.batchDeadline().toHours() + " hours"));
            }
            return;
        }
        if (state == AiClient.BatchState.FAILED) {
            failWaitingJobs(ctx, batch, AiBatchStatus.FAILED,
                    AiResponse.failed(AiFailureReason.ERROR, "The batch ended without results"));
            return;
        }

        Map<String, AiResponse> results = client.collectBatch(batch.getProviderBatchId());
        store.closeBatch(batch.getId(), AiBatchStatus.ENDED);
        for (AiJob job : store.waitingJobs(batch.getId())) {
            AiResponse response = results.getOrDefault(job.customId(),
                    AiResponse.failed(AiFailureReason.ERROR, "The batch returned no result for this job"));
            handleBatchResult(ctx, job, response);
        }
        log.info("Collected {} batch {} ({} results)", batch.getProvider(), batch.getProviderBatchId(), results.size());
    }

    private void handleBatchResult(RunContext ctx, AiJob job, AiResponse response) {
        Prepared p = rebuild(ctx, job);
        if (p == null) {
            return;
        }
        try {
            if (!response.ok()) {
                process(ctx, p, response);
                return;
            }
            AiResultValidator.Validated validated;
            try {
                validated = validator.validate(p.type(), response.text(), p.payload().sourceText(),
                        p.payload().turnCount(), p.payload().customFields());
            } catch (AiResultValidator.InvalidResultException e) {
                store.countFormatRetry(p.jobId());
                process(ctx, p, AiResponse.failed(AiFailureReason.INVALID_FORMAT, e.getMessage()));
                return;
            }
            store.saveSuccess(p.jobId(), validated, response, response.text(), true);
            afterSuccess(ctx, p, validated);
        } catch (RuntimeException e) {
            log.error("AI job {} failed unexpectedly: {}", job.getId(), e.getMessage(), e);
            store.markNotAnalysed(job.getId(), AiFailureReason.ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    private void failWaitingJobs(RunContext ctx, AiBatch batch, AiBatchStatus status, AiResponse failure) {
        store.closeBatch(batch.getId(), status);
        for (AiJob job : store.waitingJobs(batch.getId())) {
            Prepared p = rebuild(ctx, job);
            if (p != null) {
                safely(() -> process(ctx, p, failure), p);
            }
        }
    }

    /** Rebuilds a job's request from its saved data, with the same reference pack version it started with. */
    private Prepared rebuild(RunContext ctx, AiJob job) {
        try {
            ReferencePackService.ActivePack pack = referencePackService.pack(job.getReferencePackVersionId());
            Payload payload = buildPayload(ctx, pack.text(), job.getJobType(), job.getTargetId());
            if (payload == null) {
                store.markNotAnalysed(job.getId(), AiFailureReason.NO_CONTENT, "Nothing left to analyse");
                return null;
            }
            return new Prepared(job.getId(), job.getTargetId(), job.getJobType(), job.getProvider(), payload);
        } catch (RuntimeException e) {
            // e.g. the session was deleted after it was sent
            store.markNotAnalysed(job.getId(), AiFailureReason.NO_CONTENT, "Could not reload the item: " + e.getMessage());
            return null;
        }
    }
}
