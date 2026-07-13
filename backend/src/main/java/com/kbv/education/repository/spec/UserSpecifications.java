package com.kbv.education.repository.spec;

import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable {@link Specification}s for filtering/searching {@link User}. Compose
 * with {@code Specification.where(...).and(...)}; null specs are ignored.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<User> hasRole(RoleType role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("role").get("name"), role);
    }

    public static Specification<User> hasStatus(UserStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<User> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("firstName")), like),
                cb.like(cb.lower(root.get("lastName")), like),
                cb.like(cb.lower(root.get("email")), like));
    }
}
