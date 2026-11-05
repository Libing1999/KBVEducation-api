package com.kbv.education.service.ai.provider;

import com.kbv.education.entity.enums.AiProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The one AI layer (memo 17.10.2026, 1.6): the same four actions for every provider.
 * The rest of the app never needs to know which AI did the work. Implementations
 * never throw for an AI-side problem; they return a failed {@link AiResponse} instead.
 */
public interface AiClient {

    AiProvider provider();

    /** False when no API key is saved for this AI; the engine then skips it (and its backup role). */
    boolean isConfigured();

    /** How long a batch may run before its unfinished jobs go to the backup. */
    Duration batchDeadline();

    /** Normal call: immediate answer, full price. */
    AiResponse call(AiRequest request);

    /**
     * Send a batch. Returns the provider's batch id.
     *
     * @throws AiBatchSubmitException when the batch could not be created at all
     */
    String submitBatch(List<AiRequest> requests) throws AiBatchSubmitException;

    /** Check a batch: still running, or ended (with or without results). */
    BatchState checkBatch(String providerBatchId);

    /** Collect the results of an ended batch, keyed by the app's custom id. Missing ids failed. */
    Map<String, AiResponse> collectBatch(String providerBatchId);

    /** Best-effort cancel of a batch that ran past its deadline. */
    void cancelBatch(String providerBatchId);

    enum BatchState {
        RUNNING,
        ENDED,
        /** Ended without usable results (expired, cancelled or failed as a whole). */
        FAILED
    }
}
