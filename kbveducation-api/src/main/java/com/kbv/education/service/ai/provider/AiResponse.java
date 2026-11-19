package com.kbv.education.service.ai.provider;

import com.kbv.education.entity.enums.AiFailureReason;

/**
 * The outcome of one AI attempt. On success {@code text} holds the JSON reply; on
 * failure {@code failure} says why, so the engine can decide between a retry and the backup.
 */
public record AiResponse(
        boolean ok,
        String text,
        AiFailureReason failure,
        String detail,
        String model,
        Long inputTokens,
        Long outputTokens,
        Long cacheReadTokens,
        Long cacheWriteTokens
) {

    public static AiResponse success(String text, String model, Long in, Long out, Long cacheRead, Long cacheWrite) {
        return new AiResponse(true, text, null, null, model, in, out, cacheRead, cacheWrite);
    }

    public static AiResponse failed(AiFailureReason reason, String detail) {
        return new AiResponse(false, null, reason, detail, null, null, null, null, null);
    }
}
