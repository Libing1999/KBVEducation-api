package com.kbv.education.service.ai.provider;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobDestination;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.CancelBatchJobConfig;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.DeleteFileConfig;
import com.google.genai.types.File;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GetBatchJobConfig;
import com.google.genai.types.InlinedRequest;
import com.google.genai.types.InlinedResponse;
import com.google.genai.types.JobState;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;
import com.kbv.education.config.AiProperties;
import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gemini via Google's official Java library ({@code com.google.genai:google-genai}).
 * Second option for analysis, and the only AI that turns voice notes into text.
 * Use only with a paid-tier key: on the free tier Google may reuse what we send.
 */
@Slf4j
@Component
public class GeminiAiClient implements AiClient {

    private static final Duration BATCH_DEADLINE = Duration.ofHours(48);
    private static final String METADATA_KEY = "custom_id";
    /** Inline audio above this goes through the Files API (requests are capped at about 20 MB). */
    private static final int MAX_INLINE_AUDIO_BYTES = 15 * 1024 * 1024;
    private static final Set<FinishReason.Known> BLOCKED_REASONS = Set.of(
            FinishReason.Known.SAFETY, FinishReason.Known.RECITATION, FinishReason.Known.BLOCKLIST,
            FinishReason.Known.PROHIBITED_CONTENT, FinishReason.Known.SPII);

    static final String TRANSCRIBE_PROMPT = """
            Transcribe this voice note word for word, in the language the speaker uses.
            Do not translate, summarise, correct or add anything. If the speaker switches language,
            keep each part in its own language. Return only the transcript text.""";

    private final AiProperties properties;
    private final Client client;

    public GeminiAiClient(AiProperties properties) {
        this.properties = properties;
        this.client = StringUtils.hasText(properties.getGeminiApiKey())
                ? Client.builder().apiKey(properties.getGeminiApiKey()).build()
                : null;
    }

    @PreDestroy
    void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public Duration batchDeadline() {
        return BATCH_DEADLINE;
    }

    @Override
    public AiResponse call(AiRequest request) {
        if (client == null) {
            return AiResponse.failed(AiFailureReason.NOT_CONFIGURED, "No Gemini API key is saved on the server");
        }
        try {
            GenerateContentResponse response = client.models.generateContent(
                    request.model(), request.userContent(), config(request));
            return toResponse(response, request.model());
        } catch (ApiException e) {
            return fromApiException(e);
        } catch (RuntimeException e) {
            return AiResponse.failed(AiFailureReason.ERROR, "Gemini request failed: " + e.getMessage());
        }
    }

    /** Voice note to text, always as a normal call (memo 12.10.2026, 4.4). There is no backup for this. */
    public AiResponse transcribe(byte[] audio, String mimeType, String model) {
        if (client == null) {
            return AiResponse.failed(AiFailureReason.NOT_CONFIGURED, "No Gemini API key is saved on the server");
        }
        File uploaded = null;
        try {
            Part audioPart;
            if (audio.length > MAX_INLINE_AUDIO_BYTES) {
                uploaded = client.files.upload(audio, UploadFileConfig.builder().mimeType(mimeType).build());
                audioPart = Part.fromUri(uploaded.uri().orElseThrow(), mimeType);
            } else {
                audioPart = Part.fromBytes(audio, mimeType);
            }
            GenerateContentResponse response = client.models.generateContent(
                    model,
                    Content.builder().role("user").parts(List.of(audioPart, Part.fromText(TRANSCRIBE_PROMPT))).build(),
                    GenerateContentConfig.builder().maxOutputTokens((int) properties.getMaxOutputTokens()).build());
            return toResponse(response, model);
        } catch (ApiException e) {
            return fromApiException(e);
        } catch (RuntimeException e) {
            return AiResponse.failed(AiFailureReason.ERROR, "Gemini transcription failed: " + e.getMessage());
        } finally {
            if (uploaded != null && uploaded.name().isPresent()) {
                try {
                    client.files.delete(uploaded.name().get(), DeleteFileConfig.builder().build());
                } catch (RuntimeException e) {
                    log.debug("Could not delete uploaded Gemini file {}: {}", uploaded.name().get(), e.getMessage());
                }
            }
        }
    }

    @Override
    public String submitBatch(List<AiRequest> requests) throws AiBatchSubmitException {
        if (client == null) {
            throw new AiBatchSubmitException(AiFailureReason.NOT_CONFIGURED, "No Gemini API key is saved on the server");
        }
        List<InlinedRequest> inlined = new ArrayList<>();
        for (AiRequest request : requests) {
            inlined.add(InlinedRequest.builder()
                    .model(request.model())
                    .contents(Content.builder().role("user").parts(List.of(Part.fromText(request.userContent()))).build())
                    .config(config(request))
                    .metadata(Map.of(METADATA_KEY, request.customId()))
                    .build());
        }
        try {
            BatchJob job = client.batches.create(
                    requests.get(0).model(),
                    BatchJobSource.builder().inlinedRequests(inlined).build(),
                    CreateBatchJobConfig.builder().displayName("kbv-ai-" + System.currentTimeMillis()).build());
            String name = job.name().orElseThrow(() -> new IllegalStateException("Gemini returned no batch name"));
            log.info("Submitted Gemini batch {} with {} requests", name, requests.size());
            return name;
        } catch (ApiException e) {
            AiResponse failure = fromApiException(e);
            throw new AiBatchSubmitException(failure.failure(), failure.detail());
        } catch (RuntimeException e) {
            throw new AiBatchSubmitException(AiFailureReason.ERROR, "Gemini batch could not be created: " + e.getMessage());
        }
    }

    @Override
    public BatchState checkBatch(String providerBatchId) {
        try {
            BatchJob job = client.batches.get(providerBatchId, GetBatchJobConfig.builder().build());
            JobState.Known state = job.state().map(JobState::knownEnum).orElse(JobState.Known.JOB_STATE_UNSPECIFIED);
            return switch (state) {
                case JOB_STATE_SUCCEEDED, JOB_STATE_PARTIALLY_SUCCEEDED -> BatchState.ENDED;
                case JOB_STATE_FAILED, JOB_STATE_CANCELLED, JOB_STATE_EXPIRED -> BatchState.FAILED;
                default -> BatchState.RUNNING;
            };
        } catch (RuntimeException e) {
            log.warn("Could not check Gemini batch {}: {}", providerBatchId, e.getMessage());
            return BatchState.RUNNING;
        }
    }

    @Override
    public Map<String, AiResponse> collectBatch(String providerBatchId) {
        Map<String, AiResponse> results = new HashMap<>();
        try {
            BatchJob job = client.batches.get(providerBatchId, GetBatchJobConfig.builder().build());
            List<InlinedResponse> responses = job.dest().flatMap(BatchJobDestination::inlinedResponses).orElse(List.of());
            List<InlinedRequest> sent = job.src().flatMap(BatchJobSource::inlinedRequests).orElse(List.of());
            String model = job.model().orElse("gemini");
            for (int i = 0; i < responses.size(); i++) {
                InlinedResponse item = responses.get(i);
                // Match by our id; fall back to request order if Gemini did not echo the metadata.
                Optional<String> id = item.metadata().map(m -> m.get(METADATA_KEY));
                if (id.isEmpty() && i < sent.size()) {
                    id = sent.get(i).metadata().map(m -> m.get(METADATA_KEY));
                }
                if (id.isEmpty()) {
                    continue;
                }
                if (item.error().isPresent()) {
                    results.put(id.get(), AiResponse.failed(AiFailureReason.ERROR,
                            "Gemini batch item failed: " + item.error().get().message().orElse("no details")));
                } else if (item.response().isPresent()) {
                    results.put(id.get(), toResponse(item.response().get(), model));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not collect Gemini batch {}: {}", providerBatchId, e.getMessage());
        }
        return results;
    }

    @Override
    public void cancelBatch(String providerBatchId) {
        try {
            client.batches.cancel(providerBatchId, CancelBatchJobConfig.builder().build());
        } catch (RuntimeException e) {
            log.warn("Could not cancel Gemini batch {}: {}", providerBatchId, e.getMessage());
        }
    }

    // --- helpers -----------------------------------------------------------

    private GenerateContentConfig config(AiRequest request) {
        String system = StringUtils.hasText(request.referencePack())
                ? request.instructions() + "\n\n" + request.referencePack()
                : request.instructions();
        return GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(system)))
                .responseMimeType("application/json")
                .responseJsonSchema(request.schema())
                .maxOutputTokens((int) properties.getMaxOutputTokens())
                .build();
    }

    private AiResponse toResponse(GenerateContentResponse response, String requestedModel) {
        List<Candidate> candidates = response.candidates().orElse(List.of());
        if (candidates.isEmpty()) {
            boolean blocked = response.promptFeedback().flatMap(f -> f.blockReason()).isPresent();
            return blocked
                    ? AiResponse.failed(AiFailureReason.BLOCKED, "Gemini blocked the request")
                    : AiResponse.failed(AiFailureReason.EMPTY, "Gemini returned no answer");
        }
        FinishReason.Known finish = candidates.get(0).finishReason()
                .map(FinishReason::knownEnum)
                .orElse(FinishReason.Known.STOP);
        if (finish == FinishReason.Known.MAX_TOKENS) {
            return AiResponse.failed(AiFailureReason.TRUNCATED, "Gemini stopped before the end (MAX_TOKENS)");
        }
        if (BLOCKED_REASONS.contains(finish)) {
            return AiResponse.failed(AiFailureReason.BLOCKED, "Gemini blocked the answer (" + finish + ")");
        }
        String text = response.text();
        if (!StringUtils.hasText(text)) {
            return AiResponse.failed(AiFailureReason.EMPTY, "Gemini returned an empty answer");
        }
        Optional<GenerateContentResponseUsageMetadata> usage = response.usageMetadata();
        return AiResponse.success(text,
                response.modelVersion().orElse(requestedModel),
                usage.flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).map(Long::valueOf).orElse(null),
                usage.flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount).map(Long::valueOf).orElse(null),
                usage.flatMap(GenerateContentResponseUsageMetadata::cachedContentTokenCount).map(Long::valueOf).orElse(null),
                null);
    }

    private AiResponse fromApiException(ApiException e) {
        String message = e.message() == null ? "" : e.message();
        String lower = (message + " " + e.status()).toLowerCase(Locale.ROOT);
        if (lower.contains("billing") || lower.contains("quota") || lower.contains("resource_exhausted")
                || lower.contains("credit")) {
            return AiResponse.failed(AiFailureReason.NO_CREDIT, "Gemini " + e.code() + ": " + message);
        }
        return AiResponse.failed(AiFailureReason.ERROR, "Gemini " + e.code() + ": " + message);
    }
}
