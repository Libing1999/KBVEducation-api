package com.kbv.education.repository.spec;

import com.kbv.education.entity.HomeworkSubmission;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class HomeworkSubmissionSpecifications {

    private HomeworkSubmissionSpecifications() {
    }

    public static Specification<HomeworkSubmission> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<HomeworkSubmission> inLesson(UUID lessonId) {
        if (lessonId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("homework").get("lesson").get("id"), lessonId);
    }

    public static Specification<HomeworkSubmission> forStudent(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<HomeworkSubmission> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("student").get("firstName")), like),
                cb.like(cb.lower(root.get("student").get("lastName")), like),
                cb.like(cb.lower(root.get("homework").get("lesson").get("title")), like));
    }
}
