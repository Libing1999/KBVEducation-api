package com.kbv.education.service;

import com.kbv.education.dto.notification.NotificationResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;

import java.util.UUID;

/** In-app notifications (Step 6). Other services call {@link #notify} to emit. */
public interface NotificationService {

    PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size);

    long unreadCount(UUID userId);

    void markRead(UUID userId, UUID notificationId);

    void markAllRead(UUID userId);

    /** Soft-deletes one of the caller's own notifications. */
    void delete(UUID userId, UUID notificationId);

    /** Create and persist a notification for a recipient. */
    void notify(UUID recipientId, NotificationType type, String title, String message,
                ReferenceType referenceType, UUID referenceId);

    /** Emit a notification to every active student in a cohort (best-effort). */
    void notifyCohortStudents(UUID cohortId, NotificationType type, String title, String message,
                              ReferenceType referenceType, UUID referenceId);

    /** Emit a notification to every active admin (best-effort). */
    void notifyAdmins(NotificationType type, String title, String message,
                      ReferenceType referenceType, UUID referenceId);
}
