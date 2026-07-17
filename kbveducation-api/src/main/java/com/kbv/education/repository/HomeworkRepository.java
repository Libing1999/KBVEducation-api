package com.kbv.education.repository;

import com.kbv.education.entity.Homework;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkRepository extends JpaRepository<Homework, UUID> {

    Optional<Homework> findByIdAndDeletedFalse(UUID id);

    Optional<Homework> findByLesson_IdAndDeletedFalse(UUID lessonId);

    boolean existsByLesson_IdAndDeletedFalse(UUID lessonId);

    List<Homework> findByDueDateBetweenAndDeletedFalse(Instant from, Instant to);

    @Query("select h from Homework h where h.deleted = false and lower(h.title) like lower(concat('%', :q, '%'))")
    List<Homework> search(@Param("q") String query, Pageable pageable);
}
