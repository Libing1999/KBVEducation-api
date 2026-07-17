package com.kbv.education.repository;

import com.kbv.education.config.JpaAuditingConfig;
import com.kbv.education.entity.Role;
import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the ILIKE-equivalent search() query added for Global Search (Phase
 * 5 Step 7, plan decision #11) - custom JPQL that had no test coverage
 * beyond the manual curl checks done during live verification. Runs against
 * the real configured Postgres datasource (no H2/Testcontainers dependency
 * exists in this project) inside a transaction that's rolled back after each
 * test, so it leaves no residue in the dev database. JpaAuditingConfig is
 * imported explicitly because @DataJpaTest's sliced context doesn't pick up
 * the app's own @Configuration classes, and @EnableJpaAuditing's
 * auditorAwareRef bean lookup fails context startup otherwise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserRepositorySearchTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findsUsersByPartialCaseInsensitiveEmailOrName() {
        Role studentRole = roleRepository.findByName(RoleType.STUDENT).orElseThrow();
        User match = persistUser(studentRole, "zzz-search-target@kbv.edu", "Zorro", "Zephyrine");
        User nonMatch = persistUser(studentRole, "zzz-search-other@kbv.edu", "Aaron", "Aardvark");
        entityManager.flush();

        List<User> byEmailFragment = userRepository.search("SEARCH-TARGET", PageRequest.of(0, 10));
        assertThat(byEmailFragment).extracting(User::getId).contains(match.getId());
        assertThat(byEmailFragment).extracting(User::getId).doesNotContain(nonMatch.getId());

        List<User> byFirstName = userRepository.search("zorro", PageRequest.of(0, 10));
        assertThat(byFirstName).extracting(User::getId).contains(match.getId());

        List<User> byLastNameFragment = userRepository.search("ephyrin", PageRequest.of(0, 10));
        assertThat(byLastNameFragment).extracting(User::getId).contains(match.getId());
    }

    @Test
    void excludesSoftDeletedUsers() {
        Role studentRole = roleRepository.findByName(RoleType.STUDENT).orElseThrow();
        User deleted = persistUser(studentRole, "zzz-search-deleted@kbv.edu", "Deleted", "User");
        deleted.setDeleted(true);
        entityManager.persistAndFlush(deleted);

        List<User> results = userRepository.search("search-deleted", PageRequest.of(0, 10));
        assertThat(results).extracting(User::getId).doesNotContain(deleted.getId());
    }

    @Test
    void respectsThePageableLimit() {
        Role studentRole = roleRepository.findByName(RoleType.STUDENT).orElseThrow();
        for (int i = 0; i < 5; i++) {
            persistUser(studentRole, "zzz-search-limit-" + i + "@kbv.edu", "Limit" + i, "Test");
        }
        entityManager.flush();

        List<User> results = userRepository.search("zzz-search-limit", PageRequest.of(0, 3));
        assertThat(results).hasSize(3);
    }

    private User persistUser(Role role, String email, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("irrelevant-for-this-test");
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setRole(role);
        return entityManager.persistAndFlush(user);
    }
}
