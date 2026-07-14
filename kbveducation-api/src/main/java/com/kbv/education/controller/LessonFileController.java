package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.file.FileResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.LessonFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Lesson Files", description = "Lesson file management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/lessons/{lessonId}/files")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class LessonFileController {

    private final LessonFileService lessonFileService;

    @Operation(summary = "List a lesson's files")
    @GetMapping
    public ApiResponse<List<FileResponse>> list(@PathVariable UUID lessonId) {
        return ApiResponse.success(lessonFileService.listByLesson(lessonId));
    }

    @Operation(summary = "Upload one or more files to a lesson")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<List<FileResponse>>> upload(
            @PathVariable UUID lessonId,
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Files uploaded", lessonFileService.upload(lessonId, files)));
    }

    @Operation(summary = "Download a lesson file")
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID lessonId, @PathVariable UUID fileId) {
        return FileDownloads.attachment(lessonFileService.download(fileId));
    }

    @Operation(summary = "Delete a lesson file")
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@PathVariable UUID lessonId, @PathVariable UUID fileId) {
        lessonFileService.delete(fileId);
        return ApiResponse.success("File deleted");
    }
}
