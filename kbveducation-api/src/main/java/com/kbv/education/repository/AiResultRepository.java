package com.kbv.education.repository;

import com.kbv.education.entity.AiResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiResultRepository extends JpaRepository<AiResult, UUID> {

    Optional<AiResult> findFirstByTargetIdAndCurrentTrueOrderByCreatedAtDesc(UUID targetId);

    List<AiResult> findByTargetIdInAndCurrentTrue(Collection<UUID> targetIds);

    boolean existsByJobId(UUID jobId);

    @Modifying
    @Query("update AiResult r set r.current = false where r.targetId = :targetId and r.current = true")
    int markNotCurrent(@Param("targetId") UUID targetId);

    @Query("select coalesce(sum(r.inputTokens), 0), coalesce(sum(r.outputTokens), 0), "
            + "coalesce(sum(r.cacheReadTokens), 0), coalesce(sum(r.cacheWriteTokens), 0), count(r) "
            + "from AiResult r where r.createdAt >= :from and r.provider = :provider")
    List<Object[]> usageSince(@Param("from") Instant from,
                              @Param("provider") com.kbv.education.entity.enums.AiProvider provider);
}
