package com.kbv.education.repository;

import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByLockedUntilAfterAndDeletedFalse(Instant now);

    List<User> findTop5ByDeletedFalseOrderByCreatedAtDesc();

    List<User> findByRole_NameAndStatusAndDeletedFalse(RoleType role, com.kbv.education.entity.enums.UserStatus status);

    long countByRole_NameAndStatusAndDeletedFalse(RoleType role, com.kbv.education.entity.enums.UserStatus status);

    @Query("select u from User u where u.deleted = false and ("
            + "lower(u.email) like lower(concat('%', :q, '%')) "
            + "or lower(u.firstName) like lower(concat('%', :q, '%')) "
            + "or lower(u.lastName) like lower(concat('%', :q, '%')))")
    List<User> search(@Param("q") String query, Pageable pageable);
}
