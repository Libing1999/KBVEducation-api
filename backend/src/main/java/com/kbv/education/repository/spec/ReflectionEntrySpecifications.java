package com.kbv.education.repository.spec;

import com.kbv.education.entity.ReflectionEntry;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.enums.ReflectionType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ReflectionEntrySpecifications {

    private ReflectionEntrySpecifications() {
    }

    public static Specification<ReflectionEntry> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<ReflectionEntry> forStudent(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<ReflectionEntry> ofType(ReflectionType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("reflectionType"), type);
    }

    /** Restrict to students in a given cohort (active membership) via a subquery. */
    public static Specification<ReflectionEntry> inCohort(UUID cohortId) {
        if (cohortId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<StudentCohort> sc = sub.from(StudentCohort.class);
            sub.select(sc.get("student").get("id"))
                    .where(cb.and(
                            cb.equal(sc.get("cohort").get("id"), cohortId),
                            cb.isTrue(sc.get("active")),
                            cb.isFalse(sc.get("deleted"))));
            return root.get("student").get("id").in(sub);
        };
    }

    public static Specification<ReflectionEntry> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("student").get("firstName")), like),
                cb.like(cb.lower(root.get("student").get("lastName")), like));
    }
}
