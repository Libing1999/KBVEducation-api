package com.kbv.education.service.impl;

import com.kbv.education.dto.audit.ScoreAuditLogResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.ScoreAuditLog;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.repository.ScoreAuditLogRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.ScoreAuditLogService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreAuditLogServiceImpl implements ScoreAuditLogService {

    private static final List<String> SORTABLE = List.of("createdAt");

    private final ScoreAuditLogRepository scoreAuditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ScoreAuditEntityType entityType, UUID entityId, UUID studentId,
                        String action, String previousValue, String newValue, String reason) {
        try {
            ScoreAuditLog auditLog = new ScoreAuditLog();
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            if (studentId != null) {
                userRepository.findByIdAndDeletedFalse(studentId).ifPresent(auditLog::setStudent);
            }
            auditLog.setAction(action);
            auditLog.setPreviousValue(previousValue);
            auditLog.setNewValue(newValue);
            auditLog.setReason(reason);
            scoreAuditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Audit logging must never take down the primary write it accompanies.
            log.error("Failed to record score audit log entityType={} action={}", entityType, action, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScoreAuditLogResponse> list(ScoreAuditEntityType entityType, UUID studentId,
                                                      int page, int size) {
        Pageable pageable = PageableBuilder.build(page, size, "createdAt", "desc", SORTABLE);
        Page<ScoreAuditLog> result;
        if (studentId != null) {
            result = scoreAuditLogRepository.findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(studentId, pageable);
        } else if (entityType != null) {
            result = scoreAuditLogRepository.findByEntityTypeAndDeletedFalseOrderByCreatedAtDesc(entityType, pageable);
        } else {
            result = scoreAuditLogRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable);
        }
        return PageResponse.from(result, this::toResponse);
    }

    private ScoreAuditLogResponse toResponse(ScoreAuditLog auditLog) {
        return new ScoreAuditLogResponse(
                auditLog.getId(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getStudent() != null ? auditLog.getStudent().getId() : null,
                auditLog.getStudent() != null ? auditLog.getStudent().getFullName() : null,
                auditLog.getAction(),
                auditLog.getPreviousValue(),
                auditLog.getNewValue(),
                auditLog.getReason(),
                auditLog.getCreatedBy(),
                auditLog.getCreatedAt());
    }
}
