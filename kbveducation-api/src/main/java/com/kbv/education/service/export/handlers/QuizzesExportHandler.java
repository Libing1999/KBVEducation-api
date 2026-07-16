package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.QuizAttempt;
import com.kbv.education.entity.enums.AttemptStatus;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.QuizAttemptRepository;
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
public class QuizzesExportHandler implements ExportDatasetHandler {

    private final QuizAttemptRepository quizAttemptRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.QUIZZES;
    }

    @Override
    public String label() {
        return "Quiz Attempts";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Student", "Quiz", "Status", "Score", "Max Score", "Submitted At");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        AttemptStatus status = parseStatus(filters.status());
        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        Specification<QuizAttempt> spec = Specification.<QuizAttempt>where(GenericExportSpecifications.notDeleted())
                .and(GenericExportSpecifications.studentIdEquals(filters.studentId()))
                .and(GenericExportSpecifications.studentIdIn(cohortStudentIds))
                .and(GenericExportSpecifications.statusEquals(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        return quizAttemptRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(QuizAttempt attempt) {
        return List.of(
                attempt.getStudent().getFullName(), attempt.getQuiz().getTitle(), attempt.getStatus().name(),
                attempt.getScore() != null ? attempt.getScore() : "",
                attempt.getMaxScore() != null ? attempt.getMaxScore() : "",
                attempt.getSubmittedAt() != null ? attempt.getSubmittedAt().toString() : "");
    }

    @Override
    public String fileNamePrefix() {
        return "quiz-attempts";
    }

    private AttemptStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AttemptStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '%s' — expected one of %s"
                    .formatted(raw, List.of(AttemptStatus.values())));
        }
    }
}
