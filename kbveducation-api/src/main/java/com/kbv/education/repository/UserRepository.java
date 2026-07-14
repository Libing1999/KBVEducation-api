package com.kbv.education.repository;

import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link JpaSpecificationExecutor} is included to support the dynamic
 * filtering/searching used by the admin module (Step 5).
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCaseAndDeletedFalse(String email);

    Optional<User> findByIdAndDeletedFalse(UUID id);

    boolean existsByEmailIgnoreCaseAndDeletedFalse(String email);

    long countByRole_NameAndDeletedFalse(RoleType role);

    long countByLastLoginAtAfterAndDeletedFalse(Instant since);

    List<User> findTop5ByDeletedFalseOrderByCreatedAtDesc();

    List<User> findByRole_NameAndStatusAndDeletedFalse(RoleType role, com.kbv.education.entity.enums.UserStatus status);

    long countByRole_NameAndStatusAndDeletedFalse(RoleType role, com.kbv.education.entity.enums.UserStatus status);
}
