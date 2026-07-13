package com.kbv.education.config;

import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.repository.RoleRepository;
import com.kbv.education.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the initial SUPER_ADMIN account at startup (idempotent). Runs after
 * Flyway has seeded the roles. Credentials are configurable via
 * {@code app.admin.*} / environment variables and should be overridden outside
 * local development.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.first-name}")
    private String adminFirstName;

    @Value("${app.admin.last-name}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(adminEmail)) {
            log.debug("Super admin already present; skipping seed");
            return;
        }

        Role superAdminRole = roleRepository.findByName(RoleType.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException(
                        "SUPER_ADMIN role not found — ensure Flyway migration V2 has run"));

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFirstName(adminFirstName);
        admin.setLastName(adminLastName);
        admin.setRole(superAdminRole);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        log.warn("Seeded initial SUPER_ADMIN [{}]. Change this password immediately in non-dev environments.",
                adminEmail);
    }
}
