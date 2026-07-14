package com.kbv.education.service;

import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.User;
import com.kbv.education.repository.StudentCohortRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles a {@link StudentResponse} for a student {@link User}, resolving the
 * active cohort reference. Shared by the student and cohort services.
 */
@Component
@RequiredArgsConstructor
public class StudentResponseAssembler {

    private final StudentCohortRepository studentCohortRepository;

    public StudentResponse toResponse(User student) {
        StudentResponse.CohortRef cohortRef = studentCohortRepository
                .findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId())
                .map(sc -> new StudentResponse.CohortRef(
                        sc.getCohort().getId(),
                        sc.getCohort().getName(),
                        sc.getCohort().getStatus()))
                .orElse(null);

        return new StudentResponse(
                student.getId(),
                student.getEmail(),
                student.getFirstName(),
                student.getLastName(),
                student.getPhone(),
                student.getStatus(),
                cohortRef,
                student.getLastLoginAt(),
                student.getCreatedAt());
    }
}
