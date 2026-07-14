package com.kbv.education.repository;

import com.kbv.education.entity.PracticeReviewRequest;
import com.kbv.education.entity.enums.ReviewRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeReviewRequestRepository extends JpaRepository<PracticeReviewRequest, UUID>,
        JpaSpecificationExecutor<PracticeReviewRequest> {

    Optional<PracticeReviewRequest> findByIdAndDeletedFalse(UUID id);

    List<PracticeReviewRequest> findByPracticeSession_IdAndDeletedFalseOrderByCreatedAtDesc(UUID practiceSessionId);

    boolean existsByPracticeSession_IdAndStatusAndDeletedFalse(UUID practiceSessionId, ReviewRequestStatus status);

    long countByStatusAndDeletedFalse(ReviewRequestStatus status);

    Page<PracticeReviewRequest> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<PracticeReviewRequest> findByStatusAndDeletedFalseOrderByCreatedAtDesc(
            ReviewRequestStatus status, Pageable pageable);
}
