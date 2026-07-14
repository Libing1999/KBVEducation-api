package com.kbv.education.service;

import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.exception.ApiException;
import com.kbv.education.exception.DuplicateResourceException;
import com.kbv.education.exception.ErrorCode;
import com.kbv.education.repository.RoleRepository;
import com.kbv.education.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Shared provisioning logic for creating user accounts, reused by the user,
 * student, and parent services to avoid duplication (uniqueness check, password
 * hashing, role resolution).
 */
@Component
@RequiredArgsConstructor
public class AccountFactory {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public User createAccount(String email, String rawPassword, String firstName, String lastName,
                              String phone, RoleType roleType) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            throw new DuplicateResourceException("A user with email '" + email + "' already exists");
        }

        Role role = roleRepository.findByName(roleType)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Role not configured: " + roleType));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
