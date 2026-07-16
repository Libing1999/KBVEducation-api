package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.repository.ReflectionEntryRepository;
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

/** No STATUS filter — a reflection entry is submit-once, no review workflow. */
@Component
@RequiredArgsConstructor
public class ReflectionsExportHandler implements ExportDatasetHandler {

    private final ReflectionEntryRepository reflectionEntryRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.REFLECTIONS;
    }

    @Override
    public String label() {
        return "Reflections";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT);
    }

    @Override
    public List<String> headers() {
        return List.of("Student", "Reflection Date", "Type", "Submitted At");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        Specification<ReflectionEntry> spec = Specification.<ReflectionEntry>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.studentIdEquals(filters.studentId()))
                .and(GenericExportSpecifications.studentIdIn(cohortStudentIds))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return reflectionEntryRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(ReflectionEntry entry) {
        return List.of(
                entry.getStudent().getFullName(), entry.getReflectionDate().toString(),
                entry.getReflectionType().name(), entry.getSubmittedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "reflections";
    }
}
