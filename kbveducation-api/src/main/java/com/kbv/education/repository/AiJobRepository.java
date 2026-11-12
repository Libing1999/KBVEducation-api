package com.kbv.education.repository;

import com.kbv.education.entity.AiJob;
import com.kbv.education.entity.enums.AiJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    List<AiJob> findByBatchIdAndStatus(UUID batchId, AiJobStatus status);

    Optional<AiJob> findFirstByTargetIdOrderByCreatedAtDesc(UUID targetId);

    List<AiJob> findByTargetIdIn(Collection<UUID> targetIds);

    boolean existsByTargetId(UUID targetId);

    boolean existsByTargetIdAndStatusIn(UUID targetId, Collection<AiJobStatus> statuses);

    long countByStatusIn(Collection<AiJobStatus> statuses);

    long countByStatusAndCompletedAtAfter(AiJobStatus status, java.time.Instant after);
}
