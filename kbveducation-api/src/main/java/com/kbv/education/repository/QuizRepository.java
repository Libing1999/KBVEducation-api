package com.kbv.education.repository;

import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.enums.QuizStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndDeletedFalse(UUID id);

    Optional<Quiz> findByLesson_IdAndDeletedFalse(UUID lessonId);

    boolean existsByLesson_IdAndDeletedFalse(UUID lessonId);

    List<Quiz> findByStatusAndDeletedFalse(QuizStatus status);

    // --- Parent summary: "Post-Lesson Quiz: X of Y total" denominator. Homework has an
    // admin-set total (ScoreConfig.totalHomeworkCount); quizzes have no such config field, so
    // this counts published quizzes across the student's cohort as the best available total. ---
    @Query("select count(q) from Quiz q where q.deleted = false and q.status = com.kbv.education.entity.enums.QuizStatus.PUBLISHED "
            + "and q.lesson.cohort.id = :cohortId")
    long countPublishedByCohortId(@Param("cohortId") UUID cohortId);

    @Query("select q from Quiz q where q.deleted = false and lower(q.title) like lower(concat('%', :q, '%'))")
    List<Quiz> search(@Param("q") String query, Pageable pageable);
}
