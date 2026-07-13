package com.kbv.education.repository;

import com.kbv.education.entity.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findByQuestion_IdAndDeletedFalseOrderByDisplayOrderAsc(UUID questionId);

    Optional<QuizOption> findByIdAndDeletedFalse(UUID id);

    List<QuizOption> findByQuestion_IdAndCorrectTrueAndDeletedFalse(UUID questionId);
}
