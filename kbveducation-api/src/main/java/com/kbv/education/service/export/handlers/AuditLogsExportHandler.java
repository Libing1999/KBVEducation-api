package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.AuditLog;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.repository.AuditLogRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.GenericExportSpecifications;
import com.kbv.education.service.export.ExportDatasetHandler;
import com.kbv.education.service.export.ExportFilters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Only DATE applies in Step 3 — audit entries have no cohort concept, and
 * "student"/"status" don't map cleanly onto the still-growing generic action
 * list (Step 4 builds the dedicated audit-trail viewer with richer filters).
 */
@Component
@RequiredArgsConstructor
public class AuditLogsExportHandler implements ExportDatasetHandler {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.AUDIT_LOGS;
    }

    @Override
    public String label() {
        return "Audit Logs";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE);
    }

    @Override
    public List<String> headers() {
        return List.of("Actor", "Action", "Entity Type", "Entity ID", "IP Address", "Date");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        Specification<AuditLog> spec = Specification.<AuditLog>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return auditLogRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(AuditLog entry) {
        String actor = entry.getCreatedBy() != null
                ? userRepository.findByIdAndDeletedFalse(entry.getCreatedBy()).map(u -> u.getFullName()).orElse("Unknown")
                : entry.getActorEmailSnapshot() != null ? entry.getActorEmailSnapshot() : "System";
        return List.of(
                actor, entry.getAction(), entry.getEntityType(),
                entry.getEntityId() != null ? entry.getEntityId().toString() : "",
                entry.getIpAddress() != null ? entry.getIpAddress() : "",
                entry.getCreatedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "audit-logs";
    }
}
