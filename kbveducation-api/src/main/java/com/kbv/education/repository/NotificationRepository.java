package com.kbv.education.repository;

import com.kbv.education.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipient_IdAndDeletedFalse(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipient_IdAndReadFalseAndDeletedFalse(UUID recipientId, Pageable pageable);

    long countByRecipient_IdAndReadFalseAndDeletedFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipient_IdAndDeletedFalse(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true "
            + "WHERE n.recipient.id = :recipientId AND n.read = false AND n.deleted = false")
    int markAllRead(@Param("recipientId") UUID recipientId);
}
