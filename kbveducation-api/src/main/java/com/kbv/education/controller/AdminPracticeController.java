package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.practice.AdminUpdatePracticeRequest;
import com.kbv.education.dto.practice.PracticeSessionResponse;
import com.kbv.education.dto.practice.ReviewDecisionRequest;
import com.kbv.education.dto.practice.ReviewNoteRequest;
import com.kbv.education.dto.practice.ReviewRequestAdminSummary;
import com.kbv.education.dto.practice.ReviewRequestResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.entity.enums.ReviewRequestStatus;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin practice review workflow (SUPER_ADMIN only). */
@Tag(name = "Admin — Practice Review", description = "Review logged practice sessions (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/practice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPracticeController {

    private final PracticeService practiceService;

    @Operation(summary = "List practice sessions with filter and search")
    @GetMapping
    public ApiResponse<PageResponse<PracticeSessionResponse>> list(
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) PracticeStatus status,
            @RequestParam(required = false) StudyType studyType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(
                practiceService.adminList(cohortId, studentId, status, studyType, search, page, size, sort, direction));
    }

    @Operation(summary = "List re-review requests")
    @GetMapping("/review-requests")
    public ApiResponse<PageResponse<ReviewRequestAdminSummary>> reviewRequests(
            @RequestParam(required = false) ReviewRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(practiceService.adminListReviewRequests(status, page, size));
    }

    @Operation(summary = "Approve a re-review request (approves the session)")
    @PutMapping("/review-requests/{id}/approve")
    public ApiResponse<ReviewRequestResponse> approveRequest(@AuthenticationPrincipal UserPrincipal principal,
                                                             @PathVariable UUID id,
                                                             @RequestBody(required = false) ReviewNoteRequest body) {
        String notes = body == null ? null : body.notes();
        return ApiResponse.success("Review request approved",
                practiceService.resolveReviewRequest(id, principal.getId(), true, notes));
    }

    @Operation(summary = "Reject a re-review request")
    @PutMapping("/review-requests/{id}/reject")
    public ApiResponse<ReviewRequestResponse> rejectRequest(@AuthenticationPrincipal UserPrincipal principal,
                                                            @PathVariable UUID id,
                                                            @RequestBody(required = false) ReviewNoteRequest body) {
        String notes = body == null ? null : body.notes();
        return ApiResponse.success("Review request rejected",
                practiceService.resolveReviewRequest(id, principal.getId(), false, notes));
    }

    @Operation(summary = "Download a practice attachment")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId) {
        return FileDownloads.attachment(practiceService.adminDownloadFile(fileId));
    }

    @Operation(summary = "Get a practice session")
    @GetMapping("/{id}")
    public ApiResponse<PracticeSessionResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(practiceService.adminGet(id));
    }

    @Operation(summary = "Approve a practice session")
    @PutMapping("/{id}/approve")
    public ApiResponse<PracticeSessionResponse> approve(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID id,
                                                        @RequestBody(required = false) ReviewDecisionRequest body) {
        String comment = body == null ? null : body.comment();
        return ApiResponse.success("Practice approved", practiceService.approve(id, principal.getId(), comment));
    }

    @Operation(summary = "Reject a practice session")
    @PutMapping("/{id}/reject")
    public ApiResponse<PracticeSessionResponse> reject(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID id,
                                                       @RequestBody(required = false) ReviewDecisionRequest body) {
        String comment = body == null ? null : body.comment();
        return ApiResponse.success("Practice rejected", practiceService.reject(id, principal.getId(), comment));
    }

    @Operation(summary = "Edit a practice session (subject, date, duration, study type, notes, comment)")
    @PutMapping("/{id}")
    public ApiResponse<PracticeSessionResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody AdminUpdatePracticeRequest request) {
        return ApiResponse.success("Practice updated", practiceService.adminUpdate(id, request));
    }
}
