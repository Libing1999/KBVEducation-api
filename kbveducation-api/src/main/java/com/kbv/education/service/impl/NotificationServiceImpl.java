package com.kbv.education.service.impl;

import com.kbv.education.dto.notification.NotificationResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.Notification;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.NotificationType;
import com.kbv.education.entity.enums.ReferenceType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.NotificationRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.NotificationService;
import com.kbv.education.utils.InputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size) {
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipient_IdAndReadFalseAndDeletedFalse(userId, pageable)
                : notificationRepository.findByRecipient_IdAndDeletedFalse(userId, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipient_IdAndReadFalseAndDeletedFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipient_IdAndDeletedFalse(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipient_IdAndDeletedFalse(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        notification.setDeleted(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, String message,
                       ReferenceType referenceType, UUID referenceId) {
        Notification notification = new Notification();
        notification.setRecipient(userRepository.getReferenceById(recipientId));
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(InputSanitizer.sanitize(message, 2000));
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void notifyCohortStudents(UUID cohortId, NotificationType type, String title, String message,
                                     ReferenceType referenceType, UUID referenceId) {
        try {
            studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(cohortId)
                    .forEach(sc -> notify(sc.getStudent().getId(), type, title, message, referenceType, referenceId));
        } catch (Exception ex) {
            log.warn("Failed to notify cohort {} students: {}", cohortId, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void notifyAdmins(NotificationType type, String title, String message,
                             ReferenceType referenceType, UUID referenceId) {
        try {
            for (User admin : userRepository.findByRole_NameAndStatusAndDeletedFalse(
                    RoleType.SUPER_ADMIN, UserStatus.ACTIVE)) {
                notify(admin.getId(), type, title, message, referenceType, referenceId);
            }
        } catch (Exception ex) {
            log.warn("Failed to notify admins: {}", ex.getMessage());
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.isRead(),
                n.getReferenceType(),
                n.getReferenceId(),
                n.getCreatedAt());
    }
}
