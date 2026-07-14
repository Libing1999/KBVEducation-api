package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.practice.PracticeSessionResponse;
import com.kbv.education.dto.practice.ReviewRequestCreateRequest;
import com.kbv.education.dto.practice.ReviewRequestResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.StudyType;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Student practice logging and re-review requests. */
@Tag(name = "Student — Practice", description = "Log study sessions (STUDENT only)")
@RestController
@RequestMapping("/api/student/practice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentPracticeController {

    private final PracticeService practiceService;

    @Operation(summary = "Log a practice session (optional attachments)")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<PracticeSessionResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
            @RequestParam String subject,
            @RequestParam int durationMinutes,
            @RequestParam StudyType studyType,
            @RequestParam(required = false) String notes,
            @RequestParam(value = "files", required = false) MultipartFile[] files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Practice logged",
                        practiceService.create(principal.getId(), studyDate, subject, durationMinutes,
                                studyType, notes, files)));
    }

    @Operation(summary = "List my practice sessions (newest first)")
    @GetMapping
    public ApiResponse<List<PracticeSessionResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(practiceService.getMine(principal.getId()));
    }

    @Operation(summary = "Get one of my practice sessions")
    @GetMapping("/{id}")
    public ApiResponse<PracticeSessionResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID id) {
        return ApiResponse.success(practiceService.getMineById(principal.getId(), id));
    }

    @Operation(summary = "Download one of my practice attachments")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID fileId) {
        return FileDownloads.attachment(practiceService.downloadMyFile(principal.getId(), fileId));
    }

    @Operation(summary = "Request another review for a rejected session")
    @PostMapping("/{id}/review-request")
    public ResponseEntity<ApiResponse<ReviewRequestResponse>> requestReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewRequestCreateRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review requested",
                        practiceService.requestReview(principal.getId(), id, reason)));
    }
}
