package com.kbv.education.repository;

import com.kbv.education.entity.Quiz;
import com.kbv.education.entity.enums.QuizStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findByIdAndDeletedFalse(UUID id);

    Optional<Quiz> findByLesson_IdAndDeletedFalse(UUID lessonId);

    boolean existsByLesson_IdAndDeletedFalse(UUID lessonId);

    List<Quiz> findByStatusAndDeletedFalse(QuizStatus status);
}
