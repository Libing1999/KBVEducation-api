package com.kbv.education.service.ai.provider;

import com.kbv.education.entity.enums.AiEffort;

import java.util.Map;

/**
 * One job for an AI, identical for Claude and Gemini (memo 17.10.2026, 1.4: both AIs
 * get the same instructions and the same reference pack).
 *
 * @param customId      the app's own id for the job; results are matched back by it
 * @param instructions  the fixed instructions for this kind of job
 * @param referencePack the admin's reference pack, sent whole with every request
 * @param userContent   the session or reflection itself (names already removed)
 * @param schema        JSON schema the reply must follow
 * @param model         model name from the admin Settings screen
 * @param effort        Claude only: how much it thinks before answering
 */
public record AiRequest(
        String customId,
        String instructions,
        String referencePack,
        String userContent,
        Map<String, Object> schema,
        String model,
        AiEffort effort
) {
}
