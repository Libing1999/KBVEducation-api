package com.kbv.education.repository;

import com.kbv.education.entity.CoachMessageRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CoachMessageReadRepository extends JpaRepository<CoachMessageRead, UUID> {

    Optional<CoachMessageRead> findByMessage_IdAndReader_IdAndDeletedFalse(UUID messageId, UUID readerId);

    @Query("select r.message.id from CoachMessageRead r "
            + "where r.reader.id = :readerId and r.message.id in :messageIds and r.deleted = false")
    Set<UUID> findReadMessageIds(@Param("readerId") UUID readerId, @Param("messageIds") Collection<UUID> messageIds);
}
