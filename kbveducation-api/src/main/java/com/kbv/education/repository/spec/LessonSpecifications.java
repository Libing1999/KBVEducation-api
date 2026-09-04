package com.kbv.education.repository.spec;

import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.LessonStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class LessonSpecifications {

    private LessonSpecifications() {
    }

    public static Specification<Lesson> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<Lesson> inCohort(UUID cohortId) {
        if (cohortId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cohort").get("id"), cohortId);
    }

    public static Specification<Lesson> hasStatus(LessonStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Lesson> onDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("lessonDate"), date);
    }

    public static Specification<Lesson> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("summary")), like));
    }
}
