package com.kbv.education.repository;

import com.kbv.education.entity.HomeworkSubmissionFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionFileRepository extends JpaRepository<HomeworkSubmissionFile, UUID> {

    List<HomeworkSubmissionFile> findBySubmission_IdAndDeletedFalseOrderByUploadedDateAsc(UUID submissionId);

    Optional<HomeworkSubmissionFile> findByIdAndDeletedFalse(UUID id);
}
