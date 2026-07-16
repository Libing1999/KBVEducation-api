package com.kbv.education.service.impl;

import com.kbv.education.dto.audit.AuditTrailResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.AuditLog;
import com.kbv.education.repository.AuditLogRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.AuditLogSpecifications;
import com.kbv.education.service.AuditLogService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final List<String> SORTABLE = List.of("createdAt");

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, UUID entityId, String actorEmailSnapshot,
                        String oldValue, String newValue, String ipAddress, String userAgent) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setActorEmailSnapshot(actorEmailSnapshot);
            entry.setOldValue(oldValue);
            entry.setNewValue(newValue);
            entry.setIpAddress(ipAddress);
            entry.setUserAgent(userAgent);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Audit logging must never take down the primary write it accompanies.
            log.error("Failed to record audit log action={} entityType={}", action, entityType, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditTrailResponse> list(UUID actorId, String action, String entityType,
                                                  Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = Specification.<AuditLog>where(AuditLogSpecifications.notDeleted())
                .and(AuditLogSpecifications.byActor(actorId))
                .and(AuditLogSpecifications.hasAction(action))
                .and(AuditLogSpecifications.hasEntityType(entityType))
                .and(AuditLogSpecifications.createdBetween(from, to));

        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", SORTABLE);
        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countToday() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return auditLogRepository.countByCreatedAtAfterAndDeletedFalse(startOfDay);
    }

    private AuditTrailResponse toResponse(AuditLog entry) {
        String actorName = entry.getCreatedBy() != null
                ? userRepository.findByIdAndDeletedFalse(entry.getCreatedBy()).map(u -> u.getFullName()).orElse("Unknown")
                : entry.getActorEmailSnapshot() != null ? entry.getActorEmailSnapshot() : "System";
        return new AuditTrailResponse(
                entry.getId(), actorName, entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getOldValue(), entry.getNewValue(), entry.getIpAddress(), entry.getUserAgent(),
                entry.getCreatedAt());
    }
}
