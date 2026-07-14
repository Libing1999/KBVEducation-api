package com.kbv.education.repository;

import com.kbv.education.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID>,
        JpaSpecificationExecutor<HomeworkSubmission> {

    Optional<HomeworkSubmission> findByIdAndDeletedFalse(UUID id);

    Optional<HomeworkSubmission> findByHomework_IdAndStudent_IdAndDeletedFalse(UUID homeworkId, UUID studentId);

    boolean existsByHomework_IdAndStudent_IdAndDeletedFalse(UUID homeworkId, UUID studentId);

    List<HomeworkSubmission> findByStudent_IdAndDeletedFalseOrderBySubmittedAtDesc(UUID studentId);

    // --- progress statistics ---
    long countByStudent_IdAndDeletedFalse(UUID studentId);

    long countByStudent_IdAndSubmittedAtBetweenAndDeletedFalse(UUID studentId, Instant from, Instant to);

    @Query("select distinct s.homework.lesson.id from HomeworkSubmission s "
            + "where s.student.id = :sid and s.deleted = false")
    List<UUID> submittedLessonIds(@Param("sid") UUID studentId);

    @Query("select distinct s.homework.lesson.id from HomeworkSubmission s "
            + "where s.student.id = :sid and s.deleted = false and s.submittedAt between :from and :to")
    List<UUID> submittedLessonIdsBetween(@Param("sid") UUID studentId,
                                         @Param("from") Instant from, @Param("to") Instant to);
}
