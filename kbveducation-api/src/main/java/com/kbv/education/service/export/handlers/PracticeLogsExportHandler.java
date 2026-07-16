package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.PracticeSessionRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.spec.GenericExportSpecifications;
import com.kbv.education.service.export.ExportDatasetHandler;
import com.kbv.education.service.export.ExportFilters;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PracticeLogsExportHandler implements ExportDatasetHandler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.PRACTICE_LOGS;
    }

    @Override
    public String label() {
        return "Practice Logs";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Student", "Study Date", "Subject", "Duration (min)", "Study Type", "Status", "Reviewed By");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        PracticeStatus status = parseStatus(filters.status());
        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        Specification<PracticeSession> spec = Specification.<PracticeSession>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.studentIdEquals(filters.studentId()))
                .and(GenericExportSpecifications.studentIdIn(cohortStudentIds))
                .and(GenericExportSpecifications.statusEquals(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return practiceSessionRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(PracticeSession session) {
        return List.of(
                session.getStudent().getFullName(), session.getStudyDate().toString(), session.getSubject(),
                session.getDurationMinutes(), session.getStudyType().name(), session.getStatus().name(),
                session.getReviewedBy() != null ? session.getReviewedBy().getFullName() : "");
    }

    @Override
    public String fileNamePrefix() {
        return "practice-logs";
    }

    private PracticeStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PracticeStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '%s' — expected one of %s"
                    .formatted(raw, List.of(PracticeStatus.values())));
        }
    }
}
