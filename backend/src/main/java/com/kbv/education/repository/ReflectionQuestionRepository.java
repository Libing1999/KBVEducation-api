package com.kbv.education.repository;

import com.kbv.education.entity.ReflectionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReflectionQuestionRepository extends JpaRepository<ReflectionQuestion, UUID> {

    Optional<ReflectionQuestion> findByIdAndDeletedFalse(UUID id);

    List<ReflectionQuestion> findByDeletedFalseOrderByDisplayOrderAsc();

    List<ReflectionQuestion> findByEnabledTrueAndDeletedFalseOrderByDisplayOrderAsc();

    Optional<ReflectionQuestion> findFirstByDeletedFalseOrderByDisplayOrderDesc();
}
