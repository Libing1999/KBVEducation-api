package com.kbv.education.dto.message;

import com.kbv.education.entity.enums.MessageTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Staff compose request. Exactly one of {@code studentId} / {@code cohortId}
 * must be set, matching {@code targetType} — validated in the service layer
 * (a mismatch is a 422, not silently coerced).
 */
public record SendMessageRequest(
        @NotNull MessageTargetType targetType,
        UUID studentId,
        UUID cohortId,
        @NotBlank @Size(max = 60) String tag,
        @NotBlank @Size(max = 2000) String body
) {
}
