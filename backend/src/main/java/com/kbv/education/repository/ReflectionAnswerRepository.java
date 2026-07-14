package com.kbv.education.repository;

import com.kbv.education.entity.ReflectionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReflectionAnswerRepository extends JpaRepository<ReflectionAnswer, UUID> {

    List<ReflectionAnswer> findByReflectionEntry_IdAndDeletedFalse(UUID reflectionEntryId);
}
