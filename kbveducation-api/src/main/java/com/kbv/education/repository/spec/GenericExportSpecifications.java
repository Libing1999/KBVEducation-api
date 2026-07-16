package com.kbv.education.repository.spec;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Field-name-generic {@link Specification} builders shared by every export
 * dataset handler (Phase 5 Step 3) — unlike the per-entity {@code *Specifications}
 * classes elsewhere in this package, these work across any entity extending
 * {@code BaseEntity} since JPA Criteria's {@code root.get(name)} only needs
 * the field to exist, not a specific entity type. Every dataset's "date"
 * filter is uniformly {@code createdAt} (record creation time) rather than a
 * domain-specific date field, so this stays reusable instead of growing one
 * variant per entity.
 */
public final class GenericExportSpecifications {

    private GenericExportSpecifications() {
    }

    public static <T> Specification<T> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static <T> Specification<T> createdBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return (root, query, cb) -> {
            if (fromInstant != null && toInstant != null) {
                return cb.between(root.get("createdAt"), fromInstant, toInstant);
            }
            return fromInstant != null
                    ? cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant)
                    : cb.lessThan(root.get("createdAt"), toInstant);
        };
    }

    public static <T> Specification<T> idEquals(UUID id) {
        if (id == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    public static <T> Specification<T> idIn(List<UUID> ids) {
        if (ids == null) {
            return null;
        }
        return (root, query, cb) -> ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }

    public static <T> Specification<T> studentIdEquals(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId);
    }

    public static <T> Specification<T> studentIdIn(List<UUID> studentIds) {
        if (studentIds == null) {
            return null;
        }
        return (root, query, cb) -> studentIds.isEmpty()
                ? cb.disjunction() : root.get("student").get("id").in(studentIds);
    }

    public static <T> Specification<T> cohortIdEquals(UUID cohortId) {
        if (cohortId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cohort").get("id"), cohortId);
    }

    public static <T> Specification<T> statusEquals(Enum<?> status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
