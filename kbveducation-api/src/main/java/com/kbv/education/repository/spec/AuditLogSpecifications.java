package com.kbv.education.repository.spec;

import com.kbv.education.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<AuditLog> hasAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> hasEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<AuditLog> byActor(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("createdBy"), actorId);
    }

    public static Specification<AuditLog> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            }
            return from != null
                    ? cb.greaterThanOrEqualTo(root.get("createdAt"), from)
                    : cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }
}
