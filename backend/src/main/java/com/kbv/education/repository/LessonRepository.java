package com.kbv.education.repository;

import com.kbv.education.entity.Lesson;
import com.kbv.education.entity.enums.LessonStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID>, JpaSpecificationExecutor<Lesson> {

    Optional<Lesson> findByIdAndDeletedFalse(UUID id);

    List<Lesson> findByCohort_IdAndDeletedFalseOrderByDisplayOrderAscLessonNumberAsc(UUID cohortId);

    List<Lesson> findByCohort_IdAndStatusAndDeletedFalseOrderByDisplayOrderAscLessonNumberAsc(
            UUID cohortId, LessonStatus status);

    Optional<Lesson> findFirstByCohort_IdAndDeletedFalseOrderByDisplayOrderDesc(UUID cohortId);
}
