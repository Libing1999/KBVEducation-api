package com.kbv.education.repository;

import com.kbv.education.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID>,
        JpaSpecificationExecutor<QuizAttempt> {

    Optional<QuizAttempt> findByQuiz_IdAndStudent_IdAndDeletedFalse(UUID quizId, UUID studentId);

    boolean existsByQuiz_IdAndStudent_IdAndDeletedFalse(UUID quizId, UUID studentId);

    List<QuizAttempt> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    Optional<QuizAttempt> findByIdAndDeletedFalse(UUID id);

    // --- progress statistics ---
    long countByStudent_IdAndDeletedFalse(UUID studentId);

    long countByStudent_IdAndSubmittedAtBetweenAndDeletedFalse(UUID studentId, Instant from, Instant to);

    @Query("select distinct a.quiz.lesson.id from QuizAttempt a where a.student.id = :sid and a.deleted = false")
    List<UUID> completedLessonIds(@Param("sid") UUID studentId);

    @Query("select distinct a.quiz.lesson.id from QuizAttempt a "
            + "where a.student.id = :sid and a.deleted = false and a.submittedAt between :from and :to")
    List<UUID> completedLessonIdsBetween(@Param("sid") UUID studentId,
                                         @Param("from") Instant from, @Param("to") Instant to);
}
