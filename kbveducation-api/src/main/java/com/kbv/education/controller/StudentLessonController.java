package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.lesson.StudentLessonDetailResponse;
import com.kbv.education.dto.lesson.StudentLessonResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.StudentLessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Student/parent lesson access. Parents read the linked student's lessons. */
@Tag(name = "Student — Lessons", description = "Published lessons for students and parents")
@RestController
@RequestMapping("/api/student/lessons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT')")
public class StudentLessonController {

    private final StudentLessonService studentLessonService;

    @Operation(summary = "List my published lessons")
    @GetMapping
    public ApiResponse<PageResponse<StudentLessonResponse>> myLessons(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(studentLessonService.myLessons(principal.getId(), page, size));
    }

    @Operation(summary = "Get a lesson's detail")
    @GetMapping("/{id}")
    public ApiResponse<StudentLessonDetailResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ApiResponse.success(studentLessonService.getLessonDetail(principal.getId(), id));
    }

    @Operation(summary = "Download a lesson file")
    @GetMapping("/{lessonId}/files/{fileId}/download")
    public ResponseEntity<Resource> download(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID lessonId,
            @PathVariable UUID fileId) {
        return FileDownloads.attachment(
                studentLessonService.downloadLessonFile(principal.getId(), lessonId, fileId));
    }
}
