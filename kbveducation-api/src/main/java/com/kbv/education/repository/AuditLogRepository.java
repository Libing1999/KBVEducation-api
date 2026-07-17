package com.kbv.education.repository;

import com.kbv.education.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfterAndDeletedFalse(Instant from);

    @Query("select a from AuditLog a where a.deleted = false and ("
            + "lower(a.action) like lower(concat('%', :q, '%')) "
            + "or lower(a.entityType) like lower(concat('%', :q, '%')) "
            + "or lower(a.actorEmailSnapshot) like lower(concat('%', :q, '%')))")
    List<AuditLog> search(@Param("q") String query, Pageable pageable);
}
