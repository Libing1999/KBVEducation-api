package com.kbv.education.service;

import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.dto.practice.AdminUpdatePracticeRequest;
import com.kbv.education.dto.practice.PracticeSessionResponse;
import com.kbv.education.dto.practice.ReviewRequestAdminSummary;
import com.kbv.education.dto.practice.ReviewRequestResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.ReviewRequestStatus;
import com.kbv.education.entity.enums.StudyType;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Practice logging (student) and the manual review workflow (admin). Sessions
 * are saved verbatim (no AI validation) and start as PENDING_REVIEW; admins
 * review them. Parents have no access.
 */
public interface PracticeService {

    // --- student ---
    PracticeSessionResponse create(UUID studentId, LocalDate studyDate, String subject, int durationMinutes,
                                   StudyType studyType, String notes, MultipartFile[] files);

    List<PracticeSessionResponse> getMine(UUID studentId);

    PracticeSessionResponse getMineById(UUID studentId, UUID id);

    FileDownloadResult downloadMyFile(UUID studentId, UUID fileId);

    ReviewRequestResponse requestReview(UUID studentId, UUID practiceId, String reason);

    // --- admin ---
    PageResponse<PracticeSessionResponse> adminList(UUID cohortId, UUID studentId, PracticeStatus status,
                                                    StudyType studyType, String search,
                                                    int page, int size, String sort, String direction);

    PracticeSessionResponse adminGet(UUID id);

    PracticeSessionResponse approve(UUID id, UUID adminId, String comment);

    PracticeSessionResponse reject(UUID id, UUID adminId, String comment);

    PracticeSessionResponse adminUpdate(UUID id, AdminUpdatePracticeRequest request);

    FileDownloadResult adminDownloadFile(UUID fileId);

    PageResponse<ReviewRequestAdminSummary> adminListReviewRequests(ReviewRequestStatus status, int page, int size);

    ReviewRequestResponse resolveReviewRequest(UUID requestId, UUID adminId, boolean approve, String notes);
}
