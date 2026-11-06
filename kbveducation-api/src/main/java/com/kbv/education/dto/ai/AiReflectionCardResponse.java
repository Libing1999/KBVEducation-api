package com.kbv.education.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.entity.enums.TranscriptStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The AI's notes on one reflection (memo 12.10.2026, 4.4). Admin only. {@code transcript}
 * is the voice note turned into text, shown as "transcribed by AI" beside the audio.
 * {@code result} holds summary, flags (with quote, explanation, quote_verified) and needs_attention.
 */
public record AiReflectionCardResponse(
        UUID reflectionId,
        AiJobStatus status,
        AiProvider provider,
        String model,
        boolean backupUsed,
        Integer referencePackVersionNo,
        Instant analysedAt,
        AiFailureReason failureReason,
        String failureDetail,
        JsonNode result,
        boolean needsAttention,
        TranscriptStatus transcriptStatus,
        String transcript,
        String transcriptModel,
        Instant transcribedAt
) {
}
