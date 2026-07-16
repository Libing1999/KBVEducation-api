package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.GenericExportSpecifications;
import com.kbv.education.repository.spec.UserSpecifications;
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
public class StudentsExportHandler implements ExportDatasetHandler {

    private final UserRepository userRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.STUDENTS;
    }

    @Override
    public String label() {
        return "Students";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Name", "Email", "Phone", "Status", "Cohort", "Registered");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        UserStatus status = parseStatus(filters.status());
        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        Specification<User> spec = Specification.<User>where(UserSpecifications.notDeleted())
                .and(UserSpecifications.hasRole(RoleType.STUDENT))
                .and(UserSpecifications.hasStatus(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()))
                .and(GenericExportSpecifications.idEquals(filters.studentId()))
                .and(GenericExportSpecifications.idIn(cohortStudentIds));

        return userRepository.findAll(spec).stream().map(this::toRow).toList();
    }

    private List<Object> toRow(User student) {
        String cohortName = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId())
                .map(sc -> sc.getCohort().getName()).orElse("");
        return List.of(
                student.getFullName(), student.getEmail(), student.getPhone() != null ? student.getPhone() : "",
                student.getStatus().name(), cohortName, student.getCreatedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "students";
    }

    private UserStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status '%s' — expected one of %s"
                    .formatted(raw, List.of(UserStatus.values())));
        }
    }
}
