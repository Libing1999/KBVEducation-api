package com.kbv.education.repository.spec;

import com.kbv.education.entity.QuizAttempt;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class QuizAttemptSpecifications {

    private QuizAttemptSpecifications() {
    }

    public static Specification<QuizAttempt> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("deleted"));
    }

    public static Specification<QuizAttempt> inLesson(UUID lessonId) {
        if (lessonId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("quiz").get("lesson").get("id"), lessonId);
    }

    public static Specification<QuizAttempt> forStudent(UUID studentId) {
        if (studentId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("student").get("id"), studentId);
    }

    public static Specification<QuizAttempt> search(String term) {
        if (term == null || term.isBlank()) {
            return null;
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("student").get("firstName")), like),
                cb.like(cb.lower(root.get("student").get("lastName")), like),
                cb.like(cb.lower(root.get("quiz").get("title")), like));
    }
}
