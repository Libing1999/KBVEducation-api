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
import java.util.Set;
import java.util.UUID;

/**
 * Parents have no direct cohort — COHORT/STUDENT filters match a parent with at
 * least one linked student satisfying them, resolved via {@link ParentStudentRepository}
 * rather than a Specification join. A parent may have several linked students.
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
                .map(parent -> Map.entry(parent,
                        parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(parent.getId())))
                .filter(e -> matchesStudentFilter(e.getValue(), filters.studentId()))
                .filter(e -> matchesCohortFilter(e.getValue(), cohortStudentIds))
                .map(e -> toRow(e.getKey(), e.getValue()))
                .toList();
    }

    private boolean matchesStudentFilter(List<ParentStudent> links, UUID studentId) {
        return studentId == null || links.stream().anyMatch(l -> l.getStudent().getId().equals(studentId));
    }

    private boolean matchesCohortFilter(List<ParentStudent> links, List<UUID> cohortStudentIds) {
        return cohortStudentIds == null
                || links.stream().anyMatch(l -> cohortStudentIds.contains(l.getStudent().getId()));
    }

    private List<Object> toRow(User parent, List<ParentStudent> links) {
        String linkedStudents = links.stream()
                .map(l -> l.getStudent().getFullName())
                .collect(java.util.stream.Collectors.joining(", "));
        return List.of(
                parent.getFullName(), parent.getEmail(), parent.getPhone() != null ? parent.getPhone() : "",
                parent.getStatus().name(), linkedStudents,
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
