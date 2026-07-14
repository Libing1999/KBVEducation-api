package com.kbv.education.repository.spec;

import com.kbv.education.entity.Cohort;
import com.kbv.education.entity.enums.CohortStatus;
import org.springframework.data.jpa.domain.Specification;

public final class CohortSpecifications {

    private CohortSpecifications() {
    }

    public static Specification<Cohort> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Cohort> hasStatus(CohortStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Cohort> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), like);
    }
}
