package com.kbv.education.dto.notification;

import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        boolean read,
        ReferenceType referenceType,
        UUID referenceId,
        Instant createdAt
) {
}
