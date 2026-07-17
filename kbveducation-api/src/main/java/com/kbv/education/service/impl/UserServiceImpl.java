package com.kbv.education.service.impl;

import com.kbv.education.audit.Audited;
import com.kbv.education.dto.request.CreateUserRequest;
import com.kbv.education.dto.request.ResetPasswordRequest;
import com.kbv.education.dto.request.UpdateStatusRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.ResourceNotFoundException;
import com.kbv.education.mapper.UserMapper;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.repository.spec.UserSpecifications;
import com.kbv.education.security.PasswordPolicyValidator;
import com.kbv.education.service.AccountFactory;
import com.kbv.education.service.RefreshTokenService;
import com.kbv.education.service.UserService;
import com.kbv.education.utils.PageableBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final List<String> SORTABLE =
            List.of("createdAt", "firstName", "lastName", "email", "status", "lastLoginAt");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AccountFactory accountFactory;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(RoleType role, UserStatus status, String search,
                                           int page, int size, String sort, String direction) {
        Specification<User> spec = Specification.where(UserSpecifications.notDeleted())
                .and(UserSpecifications.hasRole(role))
                .and(UserSpecifications.hasStatus(status))
                .and(UserSpecifications.search(search));

        Pageable pageable = PageableBuilder.build(page, size, sort, direction, SORTABLE);
        Page<User> result = userRepository.findAll(spec, pageable);
        return PageResponse.from(result, userMapper::toUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toUserResponse(getActiveUser(id));
    }

    @Override
    @Transactional
    @Audited(action = "USER_CREATED", entityType = "USER")
    public UserResponse create(CreateUserRequest request) {
        passwordPolicyValidator.validate(request.password());
        User user = accountFactory.createAccount(request.email(), request.password(),
                request.firstName(), request.lastName(), request.phone(), request.role());
        log.info("Created user {} with role {}", user.getEmail(), request.role());
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    @Audited(action = "USER_UPDATED", entityType = "USER")
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = getActiveUser(id);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        return userMapper.toUserResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    @Audited(action = "USER_STATUS_CHANGED", entityType = "USER")
    public UserResponse updateStatus(UUID id, UpdateStatusRequest request) {
        User user = getActiveUser(id);
        user.setStatus(request.status());
        User saved = userRepository.save(user);
        if (request.status() == UserStatus.INACTIVE) {
            // Force re-authentication: an inactive account must not keep a session.
            refreshTokenService.revokeAllForUser(saved);
        }
        log.info("Set user {} status to {}", user.getEmail(), request.status());
        return userMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        passwordPolicyValidator.validate(request.newPassword());
        User user = getActiveUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        log.info("Reset password for user {}", user.getEmail());
    }

    @Override
    @Transactional
    public UserResponse unlock(UUID id) {
        User user = getActiveUser(id);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        User saved = userRepository.save(user);
        log.info("Unlocked user {}", user.getEmail());
        return userMapper.toUserResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        User user = getActiveUser(id);
        user.setDeleted(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        log.info("Soft-deleted user {}", user.getEmail());
    }

    private User getActiveUser(UUID id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }
}
