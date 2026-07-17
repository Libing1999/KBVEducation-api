package com.kbv.education.repository;

import com.kbv.education.entity.ReflectionQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReflectionQuestionRepository extends JpaRepository<ReflectionQuestion, UUID> {

    Optional<ReflectionQuestion> findByIdAndDeletedFalse(UUID id);

    List<ReflectionQuestion> findByDeletedFalseOrderByDisplayOrderAsc();

    List<ReflectionQuestion> findByEnabledTrueAndDeletedFalseOrderByDisplayOrderAsc();

    Optional<ReflectionQuestion> findFirstByDeletedFalseOrderByDisplayOrderDesc();

    @Query("select r from ReflectionQuestion r where r.deleted = false "
            + "and lower(r.questionText) like lower(concat('%', :q, '%'))")
    List<ReflectionQuestion> search(@Param("q") String query, Pageable pageable);
}
