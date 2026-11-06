package com.kbv.education.dto.reflection;

import com.kbv.education.entity.enums.AiJobStatus;
import com.kbv.education.entity.enums.ReflectionType;
import com.kbv.education.entity.enums.TranscriptStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row in the admin Daily Reflections list (newest first). The AI fields are admin-only:
 * {@code aiNeedsAttention} is set by a flagged reflection, a failed analysis, or a voice
 * note that could not be turned into text.
 */
public record AdminReflectionSummary(
        UUID id,
        UUID studentId,
        String studentName,
        String cohortName,
        LocalDate reflectionDate,
        Instant submittedAt,
        ReflectionType reflectionType,
        String textPreview,
        boolean hasAudio,
        AiJobStatus aiStatus,
        Integer aiFlagCount,
        boolean aiNeedsAttention,
        TranscriptStatus transcriptStatus
) {
}
