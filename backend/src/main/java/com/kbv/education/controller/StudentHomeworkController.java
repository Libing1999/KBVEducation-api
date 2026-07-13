package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.homework.HomeworkSubmissionResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.HomeworkSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Student homework submission + history. Parents may read the linked student's
 * submissions (read-only); only students may submit.
 */
@Tag(name = "Student — Homework", description = "Submit and view homework")
@RestController
@RequestMapping("/api/student/homework")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT')")
public class StudentHomeworkController {

    private final HomeworkSubmissionService submissionService;

    @Operation(summary = "Submit homework for a lesson (once, multiple files)")
    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(value = "/{lessonId}", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<HomeworkSubmissionResponse>> submit(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID lessonId,
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Homework submitted",
                        submissionService.submit(principal.getId(), lessonId, note, files)));
    }

    @Operation(summary = "Get my submission for a lesson")
    @GetMapping("/{lessonId}")
    public ApiResponse<HomeworkSubmissionResponse> myByLesson(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID lessonId) {
        return ApiResponse.success(submissionService.myByLesson(principal.getId(), lessonId));
    }

    @Operation(summary = "List all my submissions")
    @GetMapping
    public ApiResponse<List<HomeworkSubmissionResponse>> myAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(submissionService.myAll(principal.getId()));
    }

    @Operation(summary = "Download one of my submission files")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fileId) {
        return FileDownloads.attachment(submissionService.downloadMyFile(principal.getId(), fileId));
    }
}
