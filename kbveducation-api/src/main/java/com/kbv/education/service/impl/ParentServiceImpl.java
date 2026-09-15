package com.kbv.education.service.impl;

import com.kbv.education.dto.request.CreateParentRequest;
import com.kbv.education.dto.request.LinkStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.ParentResponse;
import com.kbv.education.entity.ParentStudent;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.repository.ParentStudentRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.UserSpecifications;
import com.kbv.education.service.AccountFactory;
import com.kbv.education.service.ParentService;
import com.kbv.education.service.RefreshTokenService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentServiceImpl implements ParentService {

    private static final List<String> SORTABLE =
            List.of("createdAt", "firstName", "lastName", "email", "status");

    private final UserRepository userRepository;
    private final ParentStudentRepository parentStudentRepository;
    private final AccountFactory accountFactory;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ParentResponse> list(UserStatus status, String search,
                                             int page, int size, String sort, String direction) {
        Specification<User> spec = Specification.where(UserSpecifications.notDeleted())
                .and(UserSpecifications.hasRole(RoleType.PARENT))
                .and(UserSpecifications.hasStatus(status))
                .and(UserSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<User> result = userRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ParentResponse get(UUID id) {
        return toResponse(getParent(id));
    }

    @Override
    @Transactional
    public ParentResponse create(CreateParentRequest request) {
        User parent = accountFactory.createAccount(request.email(), request.password(),
                request.firstName(), request.lastName(), request.phone(), RoleType.PARENT);

        if (request.studentId() != null) {
            linkInternal(parent, getStudent(request.studentId()));
        }
        log.info("Created parent {}", parent.getEmail());
        return toResponse(parent);
    }

    @Override
    @Transactional
    public ParentResponse update(UUID id, UpdateUserRequest request) {
        User parent = getParent(id);
        parent.setFirstName(request.firstName());
        parent.setLastName(request.lastName());
        parent.setPhone(request.phone());
        return toResponse(userRepository.save(parent));
    }

    @Override
    @Transactional
    public ParentResponse linkStudent(UUID parentId, LinkStudentRequest request) {
        User parent = getParent(parentId);
        User student = getStudent(request.studentId());
        linkInternal(parent, student);
        return toResponse(parent);
    }

    @Override
    @Transactional
    public void unlinkStudent(UUID parentId, UUID studentId) {
        getParent(parentId);
        ParentStudent link = parentStudentRepository.findByParent_IdAndStudent_IdAndDeletedFalse(parentId, studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Parent-student link", parentId));
        link.setDeleted(true);
        parentStudentRepository.save(link);
        log.info("Unlinked parent {} from student {}", parentId, studentId);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        User parent = getParent(id);
        parentStudentRepository.findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(id)
                .forEach(link -> {
                    link.setDeleted(true);
                    parentStudentRepository.save(link);
                });
        parent.setDeleted(true);
        userRepository.save(parent);
        refreshTokenService.revokeAllForUser(parent);
        log.info("Soft-deleted parent {}", parent.getEmail());
    }

    /** Links a parent to an additional student; a no-op if already linked to that student. */
    private void linkInternal(User parent, User student) {
        if (parentStudentRepository.existsByParent_IdAndStudent_IdAndDeletedFalse(parent.getId(), student.getId())) {
            return; // already linked to this student
        }

        ParentStudent link = new ParentStudent();
        link.setParent(parent);
        link.setStudent(student);
        parentStudentRepository.save(link);
        log.info("Linked parent {} to student {}", parent.getId(), student.getId());
    }

    private ParentResponse toResponse(User parent) {
        List<ParentResponse.StudentRef> students = parentStudentRepository
                .findAllByParent_IdAndDeletedFalseOrderByCreatedAtAsc(parent.getId()).stream()
                .map(link -> {
                    User s = link.getStudent();
                    return new ParentResponse.StudentRef(s.getId(), s.getFirstName(), s.getLastName(), s.getEmail());
                })
                .toList();

        return new ParentResponse(
                parent.getId(),
                parent.getEmail(),
                parent.getFirstName(),
                parent.getLastName(),
                parent.getPhone(),
                parent.getStatus(),
                students,
                parent.getLastLoginAt(),
                parent.getCreatedAt());
    }

    private User getParent(UUID id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Parent", id));
        if (user.getRole().getName() != RoleType.PARENT) {
            throw ResourceNotFoundException.of("Parent", id);
        }
        return user;
    }

    private User getStudent(UUID id) {
        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", id));
        if (user.getRole().getName() != RoleType.STUDENT) {
            throw ResourceNotFoundException.of("Student", id);
        }
        return user;
    }
}
