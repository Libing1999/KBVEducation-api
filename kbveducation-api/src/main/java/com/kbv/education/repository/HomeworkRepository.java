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

    // --- Parent summary: action-strip "next thing due" — nearest upcoming due date across a
    // cohort's lessons. Filtered down to "not yet submitted by this student" in the service, since
    // that check needs HomeworkSubmissionRepository. ---
    @Query("select h from Homework h where h.deleted = false and h.lesson.cohort.id = :cohortId "
            + "and h.dueDate is not null and h.dueDate >= :from order by h.dueDate asc")
    List<Homework> findUpcomingByCohortId(@Param("cohortId") UUID cohortId, @Param("from") Instant from);

    @Query("select h from Homework h where h.deleted = false and lower(h.title) like lower(concat('%', :q, '%'))")
    List<Homework> search(@Param("q") String query, Pageable pageable);
}
