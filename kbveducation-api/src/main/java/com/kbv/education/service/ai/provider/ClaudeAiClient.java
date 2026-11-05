package com.kbv.education.service.ai.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.ErrorObject;
import com.anthropic.models.ErrorType;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.anthropic.models.messages.batches.BatchCreateParams;
import com.anthropic.models.messages.batches.MessageBatch;
import com.anthropic.models.messages.batches.MessageBatchIndividualResponse;
import com.anthropic.models.messages.batches.MessageBatchResult;
import com.kbv.education.config.AiProperties;
import com.kbv.education.entity.enums.AiEffort;
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
import java.util.stream.Collectors;

/**
 * Claude via the official Java SDK (memo 17.10.2026, 1.6).
 *
 * <ul>
 *   <li>Replies use structured outputs: the JSON schema is sent in {@code output_config.format}.</li>
 *   <li>The instructions and reference pack are a cached system prefix — one-hour cache for
 *       batches, the default five minutes for normal calls (which run back to back).</li>
 *   <li>No temperature / top_p / top_k are sent: current models reject them.</li>
 *   <li>Thinking is left at the model default (adaptive); depth is controlled by effort.</li>
 * </ul>
 */
@Slf4j
@Component
public class ClaudeAiClient implements AiClient {

    private static final Duration BATCH_DEADLINE = Duration.ofHours(24);

    private final AiProperties properties;
    private final AnthropicClient client;

    public ClaudeAiClient(AiProperties properties) {
        this.properties = properties;
        this.client = StringUtils.hasText(properties.getAnthropicApiKey())
                ? AnthropicOkHttpClient.builder().apiKey(properties.getAnthropicApiKey()).build()
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
        return AiProvider.CLAUDE;
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
            return AiResponse.failed(AiFailureReason.NOT_CONFIGURED, "No Claude API key is saved on the server");
        }
        MessageCreateParams params = MessageCreateParams.builder()
                .model(request.model())
                .maxTokens(properties.getMaxOutputTokens())
                .systemOfTextBlockParams(systemBlocks(request, CacheControlEphemeral.Ttl.TTL_5M))
                .outputConfig(outputConfig(request))
                .addUserMessage(request.userContent())
                .build();
        try {
            return toResponse(client.messages().create(params));
        } catch (AnthropicServiceException e) {
            return fromServiceException(e);
        } catch (AnthropicException e) {
            return AiResponse.failed(AiFailureReason.ERROR, "Claude request failed: " + e.getMessage());
        }
    }

    @Override
    public String submitBatch(List<AiRequest> requests) throws AiBatchSubmitException {
        if (client == null) {
            throw new AiBatchSubmitException(AiFailureReason.NOT_CONFIGURED, "No Claude API key is saved on the server");
        }
        BatchCreateParams.Builder batch = BatchCreateParams.builder();
        for (AiRequest request : requests) {
            batch.addRequest(BatchCreateParams.Request.builder()
                    .customId(request.customId())
                    .params(BatchCreateParams.Request.Params.builder()
                            .model(request.model())
                            .maxTokens(properties.getMaxOutputTokens())
                            .systemOfTextBlockParams(systemBlocks(request, CacheControlEphemeral.Ttl.TTL_1H))
                            .outputConfig(outputConfig(request))
                            .addUserMessage(request.userContent())
                            .build())
                    .build());
        }
        try {
            MessageBatch created = client.messages().batches().create(batch.build());
            log.info("Submitted Claude batch {} with {} requests", created.id(), requests.size());
            return created.id();
        } catch (AnthropicServiceException e) {
            AiResponse failure = fromServiceException(e);
            throw new AiBatchSubmitException(failure.failure(), failure.detail());
        } catch (AnthropicException e) {
            throw new AiBatchSubmitException(AiFailureReason.ERROR, "Claude batch could not be created: " + e.getMessage());
        }
    }

    @Override
    public BatchState checkBatch(String providerBatchId) {
        try {
            MessageBatch batch = client.messages().batches().retrieve(providerBatchId);
            return batch.processingStatus().equals(MessageBatch.ProcessingStatus.ENDED)
                    ? BatchState.ENDED
                    : BatchState.RUNNING;
        } catch (AnthropicException e) {
            log.warn("Could not check Claude batch {}: {}", providerBatchId, e.getMessage());
            return BatchState.RUNNING;
        }
    }

    @Override
    public Map<String, AiResponse> collectBatch(String providerBatchId) {
        Map<String, AiResponse> results = new HashMap<>();
        // Results arrive in any order: match each one to its job by custom_id, never by position.
        try (StreamResponse<MessageBatchIndividualResponse> stream =
                     client.messages().batches().resultsStreaming(providerBatchId)) {
            stream.stream().forEach(item -> results.put(item.customId(), fromBatchResult(item.result())));
        } catch (AnthropicException e) {
            log.warn("Could not collect Claude batch {}: {}", providerBatchId, e.getMessage());
        }
        return results;
    }

    @Override
    public void cancelBatch(String providerBatchId) {
        try {
            client.messages().batches().cancel(providerBatchId);
        } catch (AnthropicException e) {
            log.warn("Could not cancel Claude batch {}: {}", providerBatchId, e.getMessage());
        }
    }

    @Override
    public List<AiModelOption> listModels() {
        if (client == null) {
            return List.of();
        }
        try {
            // The Models API returns the newest models first.
            List<AiModelOption> models = new ArrayList<>();
            client.models().list().autoPager().forEach(m -> models.add(new AiModelOption(m.id(), m.displayName())));
            return models;
        } catch (AnthropicException e) {
            log.warn("Could not list Claude models: {}", e.getMessage());
            return List.of();
        }
    }

    // --- helpers -----------------------------------------------------------

    /** Instructions first, then the reference pack; the cache breakpoint sits on the last block. */
    private List<TextBlockParam> systemBlocks(AiRequest request, CacheControlEphemeral.Ttl ttl) {
        CacheControlEphemeral cache = CacheControlEphemeral.builder().ttl(ttl).build();
        List<TextBlockParam> blocks = new ArrayList<>();
        boolean hasPack = StringUtils.hasText(request.referencePack());
        TextBlockParam.Builder instructions = TextBlockParam.builder().text(request.instructions());
        if (!hasPack) {
            instructions.cacheControl(cache);
        }
        blocks.add(instructions.build());
        if (hasPack) {
            blocks.add(TextBlockParam.builder().text(request.referencePack()).cacheControl(cache).build());
        }
        return blocks;
    }

    private OutputConfig outputConfig(AiRequest request) {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        request.schema().forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        return OutputConfig.builder()
                .effort(effortOf(request.effort()))
                .format(JsonOutputFormat.builder().schema(schema.build()).build())
                .build();
    }

    private OutputConfig.Effort effortOf(AiEffort effort) {
        if (effort == null) {
            return OutputConfig.Effort.MEDIUM;
        }
        return switch (effort) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
        };
    }

    private AiResponse toResponse(Message message) {
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .collect(Collectors.joining());

        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            String why = message.stopDetails().map(Object::toString).orElse("no details");
            return AiResponse.failed(AiFailureReason.BLOCKED, "Claude declined: " + why);
        }
        if (StopReason.MAX_TOKENS.equals(stop) || StopReason.MODEL_CONTEXT_WINDOW_EXCEEDED.equals(stop)) {
            return AiResponse.failed(AiFailureReason.TRUNCATED, "Claude stopped before the end (" + stop + ")");
        }
        if (!StringUtils.hasText(text)) {
            return AiResponse.failed(AiFailureReason.EMPTY, "Claude returned an empty answer");
        }
        Usage usage = message.usage();
        return AiResponse.success(text, message.model().asString(),
                usage.inputTokens(), usage.outputTokens(),
                usage.cacheReadInputTokens().orElse(null),
                usage.cacheCreationInputTokens().orElse(null));
    }

    private AiResponse fromBatchResult(MessageBatchResult result) {
        if (result.isSucceeded()) {
            return toResponse(result.asSucceeded().message());
        }
        if (result.isErrored()) {
            ErrorObject error = result.asErrored().error().error();
            String message = errorMessage(error);
            if (error.isBillingError() || isCreditMessage(message)) {
                return AiResponse.failed(AiFailureReason.NO_CREDIT, message);
            }
            return AiResponse.failed(AiFailureReason.ERROR, message);
        }
        if (result.isExpired()) {
            return AiResponse.failed(AiFailureReason.EXPIRED, "Claude batch request expired before it was processed");
        }
        return AiResponse.failed(AiFailureReason.EXPIRED, "Claude batch request was cancelled");
    }

    private AiResponse fromServiceException(AnthropicServiceException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        boolean billing = e.errorType().map(ErrorType.BILLING_ERROR::equals).orElse(false);
        if (billing || isCreditMessage(message)) {
            return AiResponse.failed(AiFailureReason.NO_CREDIT, "Claude: " + message);
        }
        return AiResponse.failed(AiFailureReason.ERROR, "Claude error " + e.statusCode() + ": " + message);
    }

    private String errorMessage(ErrorObject error) {
        if (error.isInvalidRequestError()) {
            return error.asInvalidRequestError().message();
        }
        if (error.isBillingError()) {
            return error.asBillingError().message();
        }
        return error.toString();
    }

    /**
     * Out of credit ("Your credit balance is too low to access the Anthropic API") or the
     * workspace's monthly spending limit reached. Both count as a failed job (memo 1.6),
     * so the backup AI takes over.
     */
    private boolean isCreditMessage(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return m.contains("credit balance") || m.contains("spend limit") || m.contains("spending limit")
                || m.contains("usage limit");
    }
}
