package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.homework.HomeworkSubmissionResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.service.HomeworkSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Homework Submissions", description = "View student submissions (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/homework")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class HomeworkSubmissionAdminController {

    private final HomeworkSubmissionService submissionService;

    @Operation(summary = "List homework submissions with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<HomeworkSubmissionResponse>> list(
            @RequestParam(required = false) UUID lessonId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submittedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(
                submissionService.adminList(lessonId, studentId, search, page, size, sort, direction));
    }

    @Operation(summary = "Download a submission file")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID fileId) {
        return FileDownloads.attachment(submissionService.downloadFile(fileId));
    }
}
