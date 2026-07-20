package com.kbv.education.service.impl;

import com.kbv.education.dto.request.AssignCohortRequest;
import com.kbv.education.dto.request.CreateStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.event.StudentCohortAssignedEvent;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.CohortRepository;
import com.kbv.education.repository.StudentCohortRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.UserSpecifications;
import com.kbv.education.service.AccountFactory;
import com.kbv.education.service.RefreshTokenService;
import com.kbv.education.service.StudentResponseAssembler;
import com.kbv.education.service.StudentService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final List<String> SORTABLE =
            List.of("createdAt", "firstName", "lastName", "email", "status");

    private final UserRepository userRepository;
    private final CohortRepository cohortRepository;
    private final StudentCohortRepository studentCohortRepository;
    private final AccountFactory accountFactory;
    private final StudentResponseAssembler assembler;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> list(UserStatus status, String search,
                                              int page, int size, String sort, String direction) {
        Specification<User> spec = Specification.where(UserSpecifications.notDeleted())
                .and(UserSpecifications.hasRole(RoleType.STUDENT))
                .and(UserSpecifications.hasStatus(status))
                .and(UserSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<User> result = userRepository.findAll(spec, pageable);
        return PageResponse.from(result, assembler::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentResponse get(UUID id) {
        return assembler.toResponse(getStudent(id));
    }

    @Override
    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        User student = accountFactory.createAccount(request.email(), request.password(),
                request.firstName(), request.lastName(), request.phone(), RoleType.STUDENT);

        if (request.cohortId() != null) {
            assignInternal(student, getCohort(request.cohortId()));
        }
        log.info("Created student {}", student.getEmail());
        return assembler.toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse update(UUID id, UpdateUserRequest request) {
        User student = getStudent(id);
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setPhone(request.phone());
        return assembler.toResponse(userRepository.save(student));
    }

    @Override
    @Transactional
    public StudentResponse assignCohort(UUID studentId, AssignCohortRequest request) {
        User student = getStudent(studentId);
        Cohort cohort = getCohort(request.cohortId());
        assignInternal(student, cohort);
        return assembler.toResponse(student);
    }

    @Override
    @Transactional
    public void removeFromCohort(UUID studentId) {
        getStudent(studentId);
        studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(studentId)
                .ifPresent(sc -> {
                    sc.setActive(false);
                    studentCohortRepository.save(sc);
                    log.info("Removed student {} from cohort {}", studentId, sc.getCohort().getId());
                });
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        User student = getStudent(id);
        studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(id)
                .ifPresent(sc -> {
                    sc.setActive(false);
                    studentCohortRepository.save(sc);
                });
        student.setDeleted(true);
        userRepository.save(student);
        refreshTokenService.revokeAllForUser(student);
        log.info("Soft-deleted student {}", student.getEmail());
    }

    /**
     * Assign a student to a cohort, enforcing: cohort not archived, capacity not
     * exceeded, and the single-active-cohort rule (any prior active assignment is
     * deactivated first).
     */
    private void assignInternal(User student, Cohort cohort) {
        if (cohort.getStatus() == CohortStatus.ARCHIVED) {
            throw new BusinessRuleException("Cannot assign a student to an archived cohort");
        }

        var currentActive = studentCohortRepository.findByStudent_IdAndActiveTrueAndDeletedFalse(student.getId());
        if (currentActive.isPresent() && currentActive.get().getCohort().getId().equals(cohort.getId())) {
            return; // already assigned to this cohort
        }

        if (cohort.getMaxStudents() > 0) {
            long activeCount = studentCohortRepository.countByCohort_IdAndActiveTrueAndDeletedFalse(cohort.getId());
            if (activeCount >= cohort.getMaxStudents()) {
                throw new BusinessRuleException("Cohort '" + cohort.getName() + "' has reached its maximum capacity");
            }
        }

        // Deactivate and flush the current active assignment before activating the
        // new one so the "one active cohort per student" partial unique index holds.
        currentActive.ifPresent(sc -> {
            sc.setActive(false);
            studentCohortRepository.saveAndFlush(sc);
        });

        StudentCohort assignment = studentCohortRepository
                .findByStudent_IdAndCohort_IdAndDeletedFalse(student.getId(), cohort.getId())
                .orElseGet(() -> {
                    StudentCohort sc = new StudentCohort();
                    sc.setStudent(student);
                    sc.setCohort(cohort);
                    return sc;
                });
        assignment.setActive(true);
        assignment.setAssignedAt(Instant.now());
        studentCohortRepository.save(assignment);
        log.info("Assigned student {} to cohort {}", student.getId(), cohort.getId());

        // Consumed AFTER_COMMIT by the email listener: fires for first
        // assignments and cohort moves alike, but not for the no-op above.
        eventPublisher.publishEvent(new StudentCohortAssignedEvent(student.getId(), cohort.getId()));
    }

    private User getStudent(UUID id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", id));
        if (user.getRole().getName() != RoleType.STUDENT) {
            throw ResourceNotFoundException.of("Student", id);
        }
        return user;
    }

    private Cohort getCohort(UUID id) {
        return cohortRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cohort", id));
    }
}
