package com.kbv.education.dto.reflection;

import com.kbv.education.entity.enums.ReflectionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Row in the admin Daily Reflections list (newest first). */
public record AdminReflectionSummary(
        UUID id,
        UUID studentId,
        String studentName,
        String cohortName,
        LocalDate reflectionDate,
        Instant submittedAt,
        ReflectionType reflectionType,
        String textPreview,
        boolean hasAudio
) {
}
