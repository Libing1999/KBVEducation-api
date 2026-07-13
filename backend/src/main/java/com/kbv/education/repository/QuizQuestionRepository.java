package com.kbv.education.repository;

import com.kbv.education.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuiz_IdAndDeletedFalseOrderByDisplayOrderAsc(UUID quizId);

    Optional<QuizQuestion> findByIdAndDeletedFalse(UUID id);

    Optional<QuizQuestion> findFirstByQuiz_IdAndDeletedFalseOrderByDisplayOrderDesc(UUID quizId);

    long countByQuiz_IdAndDeletedFalse(UUID quizId);
}
