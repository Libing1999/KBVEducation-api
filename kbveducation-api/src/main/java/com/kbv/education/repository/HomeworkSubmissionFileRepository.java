package com.kbv.education.repository;

import com.kbv.education.entity.HomeworkSubmissionFile;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeworkSubmissionFileRepository extends JpaRepository<HomeworkSubmissionFile, UUID> {

    List<HomeworkSubmissionFile> findBySubmission_IdAndDeletedFalseOrderByUploadedDateAsc(UUID submissionId);

    Optional<HomeworkSubmissionFile> findByIdAndDeletedFalse(UUID id);

    /** (submission id, number of files) for the given submissions. */
    @Query("select f.submission.id, count(f) from HomeworkSubmissionFile f "
            + "where f.deleted = false and f.submission.id in :ids group by f.submission.id")
    List<Object[]> countFilesBySubmission(@Param("ids") Collection<UUID> submissionIds);
}
