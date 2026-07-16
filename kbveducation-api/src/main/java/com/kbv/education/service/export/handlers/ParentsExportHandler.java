package com.kbv.education.service.export.handlers;

import com.kbv.education.entity.ParentStudent;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.entity.enums.ExportFilterType;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.repository.ParentStudentRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Parents have no direct cohort — COHORT/STUDENT filters match a parent whose
 * linked student satisfies them, resolved via {@link ParentStudentRepository}
 * (Phase 1: one student per parent) rather than a Specification join.
 */
@Component
@RequiredArgsConstructor
public class ParentsExportHandler implements ExportDatasetHandler {

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final StudentCohortRepository studentCohortRepository;

    @Override
    public ExportDataset dataset() {
        return ExportDataset.PARENTS;
    }

    @Override
    public String label() {
        return "Parents";
    }

    @Override
    public Set<ExportFilterType> supportedFilters() {
        return EnumSet.of(ExportFilterType.DATE, ExportFilterType.COHORT, ExportFilterType.STUDENT, ExportFilterType.STATUS);
    }

    @Override
    public List<String> headers() {
        return List.of("Name", "Email", "Phone", "Status", "Linked Student", "Registered");
    }

    @Override
    public List<List<Object>> rows(ExportFilters filters) {
        UserStatus status = parseStatus(filters.status());
        Specification<User> spec = Specification.<User>where(UserSpecifications.notDeleted())
                .and(UserSpecifications.hasRole(RoleType.PARENT))
                .and(UserSpecifications.hasStatus(status))
                .and(GenericExportSpecifications.createdBetween(filters.from(), filters.to()));

        List<UUID> cohortStudentIds = filters.cohortId() == null ? null
                : studentCohortRepository.findByCohort_IdAndActiveTrueAndDeletedFalse(filters.cohortId()).stream()
                        .map(sc -> sc.getStudent().getId()).toList();

        return userRepository.findAll(spec).stream()
                .map(parent -> Map.entry(parent, parentStudentRepository.findByParent_IdAndDeletedFalse(parent.getId())))
                .filter(e -> matchesStudentFilter(e.getValue(), filters.studentId()))
                .filter(e -> matchesCohortFilter(e.getValue(), cohortStudentIds))
                .map(e -> toRow(e.getKey(), e.getValue().map(ParentStudent::getStudent).orElse(null)))
                .toList();
    }

    private boolean matchesStudentFilter(Optional<ParentStudent> link, UUID studentId) {
        return studentId == null || link.map(l -> l.getStudent().getId().equals(studentId)).orElse(false);
    }

    private boolean matchesCohortFilter(Optional<ParentStudent> link, List<UUID> cohortStudentIds) {
        return cohortStudentIds == null
                || link.map(l -> cohortStudentIds.contains(l.getStudent().getId())).orElse(false);
    }

    private List<Object> toRow(User parent, User linkedStudent) {
        return List.of(
                parent.getFullName(), parent.getEmail(), parent.getPhone() != null ? parent.getPhone() : "",
                parent.getStatus().name(), linkedStudent != null ? linkedStudent.getFullName() : "",
                parent.getCreatedAt().toString());
    }

    @Override
    public String fileNamePrefix() {
        return "parents";
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
