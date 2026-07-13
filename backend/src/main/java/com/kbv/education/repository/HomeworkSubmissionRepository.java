package com.kbv.education.repository;

import com.kbv.education.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID>,
        JpaSpecificationExecutor<HomeworkSubmission> {

    Optional<HomeworkSubmission> findByIdAndDeletedFalse(UUID id);

    Optional<HomeworkSubmission> findByHomework_IdAndStudent_IdAndDeletedFalse(UUID homeworkId, UUID studentId);

    boolean existsByHomework_IdAndStudent_IdAndDeletedFalse(UUID homeworkId, UUID studentId);

    List<HomeworkSubmission> findByStudent_IdAndDeletedFalseOrderBySubmittedAtDesc(UUID studentId);
}
