package com.kbv.education.controller;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.lesson.CreateLessonRequest;
import com.kbv.education.dto.lesson.LessonResponse;
import com.kbv.education.dto.lesson.UpdateLessonRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LessonStatus;
import com.kbv.education.service.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Lessons", description = "Lesson management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/lessons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "List lessons with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<LessonResponse>> list(
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) LessonStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "displayOrder") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return ApiResponse.success(lessonService.list(cohortId, status, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a lesson by id")
    @GetMapping("/{id}")
    public ApiResponse<LessonResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(lessonService.get(id));
    }

    @Operation(summary = "Create a lesson")
    @PostMapping
    public ResponseEntity<ApiResponse<LessonResponse>> create(@Valid @RequestBody CreateLessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lesson created", lessonService.create(request)));
    }

    @Operation(summary = "Update a lesson")
    @PutMapping("/{id}")
    public ApiResponse<LessonResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateLessonRequest request) {
        return ApiResponse.success("Lesson updated", lessonService.update(id, request));
    }

    @Operation(summary = "Soft-delete a lesson")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        lessonService.delete(id);
        return ApiResponse.success("Lesson deleted");
    }

    @Operation(summary = "Publish a lesson")
    @PostMapping("/{id}/publish")
    public ApiResponse<LessonResponse> publish(@PathVariable UUID id) {
        return ApiResponse.success("Lesson published", lessonService.publish(id));
    }

    @Operation(summary = "Unpublish a lesson")
    @PostMapping("/{id}/unpublish")
    public ApiResponse<LessonResponse> unpublish(@PathVariable UUID id) {
        return ApiResponse.success("Lesson unpublished", lessonService.unpublish(id));
    }

    @Operation(summary = "Duplicate a lesson (as a draft)")
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<LessonResponse>> duplicate(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Lesson duplicated", lessonService.duplicate(id)));
    }

    @Operation(summary = "Reorder lessons")
    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        lessonService.reorder(request);
        return ApiResponse.success("Lessons reordered");
    }
}
