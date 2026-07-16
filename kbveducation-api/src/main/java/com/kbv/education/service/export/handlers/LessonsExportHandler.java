package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.entity.enums.LessonStatus;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.LessonRepository;
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
public class LessonsExportHandler implements ExportDatasetHandler {

    private final LessonRepository lessonRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.LESSONS;
    }

    @Override
    public String label() {
        return "Lessons";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Title", "Cohort", "Lesson Number", "Status", "Lesson Date", "Published Date");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        LessonStatus status = parseStatus(filters.status());
        Specification<Lesson> spec = Specification.<Lesson>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.cohortIdEquals(filters.cohortId()))
                .and(GenericExportSpecifications.statusEquals(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return lessonRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(Lesson lesson) {
        return List.of(
                lesson.getTitle(), lesson.getCohort().getName(), lesson.getLessonNumber(),
                lesson.getStatus().name(),
                lesson.getLessonDate() != null ? lesson.getLessonDate().toString() : "",
                lesson.getPublishedDate() != null ? lesson.getPublishedDate().toString() : "");
    }

    @Override
    public String fileNamePrefix() {
        return "lessons";
    }

    private LessonStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LessonStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '%s' — expected one of %s"
                    .formatted(raw, List.of(LessonStatus.values())));
        }
    }
}
