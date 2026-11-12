package com.kbv.education.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbv.education.entity.enums.AiFailureReason;
import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.AiProvider;
import com.kbv.education.entity.enums.StudyType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The review card for one practice session (memo 12.10.2026, 4.2). Admin only.
 * {@code result} is the AI's checked reply: recommendation, trust_rating, summary,
 * past_paper_score, engagement, topics, weak_topics and flags (each flag with its
 * quote, explanation, turn_from/turn_to and quote_verified).
 */
public record AiPracticeCardResponse(
        UUID sessionId,
        AiJobStatus status,
        AiProvider provider,
        String model,
        boolean backupUsed,
        Integer referencePackVersionNo,
        boolean viaBatch,
        Instant analysedAt,
        AiFailureReason failureReason,
        String failureDetail,
        JsonNode result,
        Length length,
        String turnSplitMethod,
        List<Turn> turns,
        String overrideReason
) {

    /** Counted by the app, not the AI: this session beside the class median and the student's own median. */
    public record Length(StudyType studyType, Integer chars, Integer turns,
                         Integer classMedianChars, Integer classMedianTurns,
                         Integer studentMedianChars, Integer studentMedianTurns) {
    }

    /** One numbered turn of the original transcript, for "turns 14 to 16" pointers. */
    public record Turn(int n, String speaker, String text) {
    }
}
