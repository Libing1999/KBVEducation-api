package com.kbv.education.controller;

import com.kbv.education.dto.homework.HomeworkRequest;
import com.kbv.education.dto.homework.HomeworkResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.HomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Homework", description = "Homework configuration (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class HomeworkController {

    private final HomeworkService homeworkService;

    @Operation(summary = "Get a lesson's homework configuration")
    @GetMapping("/lessons/{lessonId}/homework")
    public ApiResponse<HomeworkResponse> getByLesson(@PathVariable UUID lessonId) {
        return ApiResponse.success(homeworkService.getByLesson(lessonId));
    }

    @Operation(summary = "Create or update a lesson's homework configuration")
    @PutMapping("/lessons/{lessonId}/homework")
    public ApiResponse<HomeworkResponse> upsert(@PathVariable UUID lessonId,
                                                @Valid @RequestBody HomeworkRequest request) {
        return ApiResponse.success("Homework saved", homeworkService.createOrUpdateForLesson(lessonId, request));
    }

    @Operation(summary = "Delete a homework configuration")
    @DeleteMapping("/homework/{homeworkId}")
    public ApiResponse<Void> delete(@PathVariable UUID homeworkId) {
        homeworkService.delete(homeworkId);
        return ApiResponse.success("Homework deleted");
    }
}
