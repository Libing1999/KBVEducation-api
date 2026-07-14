package com.kbv.education.dto.reflection;

import com.kbv.education.entity.enums.ReflectionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full view of a reflection. Used by the student (their own) and the admin
 * panel (detail). {@code editable} is true only on the reflection's own day.
 */
public record ReflectionResponse(
        UUID id,
        UUID studentId,
        String studentName,
        String cohortName,
        LocalDate reflectionDate,
        ReflectionType reflectionType,
        Instant submittedAt,
        boolean editable,
        boolean hasAudio,
        String audioFileName,
        List<ReflectionAnswerView> answers
) {
}
