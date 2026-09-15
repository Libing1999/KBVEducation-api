package com.kbv.education.dto.message;

import com.kbv.education.entity.enums.MessageTargetType;

import java.time.Instant;
import java.util.UUID;

/** Student-facing "Live Action" drawer entry, with a read flag computed for that student. */
public record StudentMessageResponse(
        UUID id,
        MessageTargetType targetType,
        String tag,
        String text,
        Instant createdAt,
        boolean read
) {
}
