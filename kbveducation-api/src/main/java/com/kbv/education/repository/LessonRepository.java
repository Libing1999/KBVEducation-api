package com.kbv.education.repository;

import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.LessonStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID>, JpaSpecificationExecutor<Lesson> {

    Optional<Lesson> findByIdAndDeletedFalse(UUID id);

    List<Lesson> findByCohort_IdAndDeletedFalseOrderByDisplayOrderAscLessonNumberAsc(UUID cohortId);

    List<Lesson> findByCohort_IdAndStatusAndDeletedFalseOrderByDisplayOrderAscLessonNumberAsc(
            UUID cohortId, LessonStatus status);

    Optional<Lesson> findFirstByCohort_IdAndDeletedFalseOrderByDisplayOrderDesc(UUID cohortId);

    @Query("select l from Lesson l where l.deleted = false and lower(l.title) like lower(concat('%', :q, '%'))")
    List<Lesson> search(@Param("q") String query, Pageable pageable);
}
