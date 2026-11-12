package com.kbv.education.repository.spec;

import com.kbv.education.dto.practice.AiPracticeFilter;
import com.kbv.education.entity.PracticeSession;
import com.kbv.education.entity.StudentCohort;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.StudyType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PracticeSessionSpecifications {

    private PracticeSessionSpecifications() {
    }

    public static Specification<PracticeSession> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<PracticeSession> forStudent(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<PracticeSession> ofStatus(PracticeStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<PracticeSession> ofType(StudyType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("studyType"), type);
    }

    /** Restrict to students in a given cohort (active membership) via a subquery. */
    public static Specification<PracticeSession> inCohort(UUID cohortId) {
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

    /** Filters on the AI headline copied onto the session (admin review list). */
    public static Specification<PracticeSession> aiFilter(AiPracticeFilter f) {
        if (f == null) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.recommendation() != null && !f.recommendation().isBlank()) {
                ps.add(cb.equal(root.get("aiRecommendation"), f.recommendation()));
            }
            if (f.minRating() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("aiTrustRating"), f.minRating()));
            }
            if (f.maxRating() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("aiTrustRating"), f.maxRating()));
            }
            if (Boolean.TRUE.equals(f.flagged())) {
                ps.add(cb.greaterThan(root.get("aiFlagCount"), 0));
            } else if (Boolean.FALSE.equals(f.flagged())) {
                ps.add(cb.equal(root.get("aiFlagCount"), 0));
            }
            if (f.aiStatus() != null) {
                ps.add(cb.equal(root.get("aiStatus"), f.aiStatus()));
            }
            if (f.autoApproved() != null) {
                ps.add(cb.equal(root.get("autoApproved"), f.autoApproved()));
            }
            if (f.spotCheck() != null) {
                ps.add(cb.equal(root.get("spotCheck"), f.spotCheck()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    public static Specification<PracticeSession> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("subject")), like),
                cb.like(cb.lower(root.get("student").get("firstName")), like),
                cb.like(cb.lower(root.get("student").get("lastName")), like));
    }
}
