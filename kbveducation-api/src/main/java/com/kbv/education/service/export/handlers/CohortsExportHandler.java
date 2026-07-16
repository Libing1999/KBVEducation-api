package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.CohortRepository;
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

@Component
@RequiredArgsConstructor
public class CohortsExportHandler implements ExportDatasetHandler {

    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.COHORTS;
    }

    @Override
    public String label() {
        return "Cohorts";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Name", "Status", "Start Date", "End Date", "Exam Date", "Max Students", "Current Students");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        CohortStatus status = parseStatus(filters.status());
        Specification<Cohort> spec = Specification.<Cohort>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.idEquals(filters.cohortId()))
                .and(GenericExportSpecifications.statusEquals(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return cohortRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(Cohort cohort) {
        long studentCount = studentCohortRepository.countByCohort_IdAndActiveTrueAndDeletedFalse(cohort.getId());
        return List.of(
                cohort.getName(), cohort.getStatus().name(),
                cohort.getStartDate() != null ? cohort.getStartDate().toString() : "",
                cohort.getEndDate() != null ? cohort.getEndDate().toString() : "",
                cohort.getExamDate() != null ? cohort.getExamDate().toString() : "",
                cohort.getMaxStudents(), studentCount);
    }

    @Override
    public String fileNamePrefix() {
        return "cohorts";
    }

    private CohortStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CohortStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '%s' — expected one of %s"
                    .formatted(raw, List.of(CohortStatus.values())));
        }
    }
}
