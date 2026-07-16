package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.HomeworkSubmission;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.repository.HomeworkSubmissionRepository;
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

/**
 * No STATUS filter — a {@link HomeworkSubmission} has no status field
 * (submit-once, no review workflow), unlike quiz attempts or practice logs.
 */
@Component
@RequiredArgsConstructor
public class HomeworkExportHandler implements ExportDatasetHandler {

    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.HOMEWORK;
    }

    @Override
    public String label() {
        return "Homework Submissions";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT);
    }

    @Override
    public List<String> headers() {
        return List.of("Student", "Homework", "Lesson", "Cohort", "Submitted At");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        Specification<HomeworkSubmission> spec = Specification.<HomeworkSubmission>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.studentIdEquals(filters.studentId()))
                .and(GenericExportSpecifications.studentIdIn(cohortStudentIds))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return homeworkSubmissionRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(HomeworkSubmission submission) {
        return List.of(
                submission.getStudent().getFullName(),
                submission.getHomework().getTitle(),
                submission.getHomework().getLesson().getTitle(),
                submission.getHomework().getLesson().getCohort().getName(),
                submission.getSubmittedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "homework-submissions";
    }
}
