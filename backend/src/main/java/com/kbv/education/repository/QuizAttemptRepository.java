package com.kbv.education.repository;

import com.kbv.education.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID>,
        JpaSpecificationExecutor<QuizAttempt> {

    Optional<QuizAttempt> findByQuiz_IdAndStudent_IdAndDeletedFalse(UUID quizId, UUID studentId);

    boolean existsByQuiz_IdAndStudent_IdAndDeletedFalse(UUID quizId, UUID studentId);

    List<QuizAttempt> findByStudent_IdAndDeletedFalseOrderByCreatedAtDesc(UUID studentId);

    Optional<QuizAttempt> findByIdAndDeletedFalse(UUID id);
}
