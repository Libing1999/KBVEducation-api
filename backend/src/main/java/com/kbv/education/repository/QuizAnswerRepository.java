package com.kbv.education.repository;

import com.kbv.education.entity.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    List<QuizAnswer> findByAttempt_IdAndDeletedFalse(UUID attemptId);
}
