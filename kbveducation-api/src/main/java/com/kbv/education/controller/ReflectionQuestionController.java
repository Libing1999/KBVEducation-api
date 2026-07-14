package com.kbv.education.controller;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionRequest;
import com.kbv.education.dto.reflection.ReflectionQuestionResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.ReflectionQuestionService;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Reflection Questions", description = "Configure daily reflection questions (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/reflection-questions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ReflectionQuestionController {

    private final ReflectionQuestionService questionService;

    @Operation(summary = "List all reflection questions")
    @GetMapping
    public ApiResponse<List<ReflectionQuestionResponse>> list() {
        return ApiResponse.success(questionService.listAll());
    }

    @Operation(summary = "Create a reflection question")
    @PostMapping
    public ResponseEntity<ApiResponse<ReflectionQuestionResponse>> create(
            @Valid @RequestBody ReflectionQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question created", questionService.create(request)));
    }

    @Operation(summary = "Update a reflection question")
    @PutMapping("/{id}")
    public ApiResponse<ReflectionQuestionResponse> update(@PathVariable UUID id,
                                                          @Valid @RequestBody ReflectionQuestionRequest request) {
        return ApiResponse.success("Question updated", questionService.update(id, request));
    }

    @Operation(summary = "Enable or disable a reflection question")
    @PatchMapping("/{id}/enabled")
    public ApiResponse<ReflectionQuestionResponse> setEnabled(@PathVariable UUID id,
                                                              @RequestParam boolean enabled) {
        return ApiResponse.success(questionService.setEnabled(id, enabled));
    }

    @Operation(summary = "Reorder reflection questions")
    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        questionService.reorder(request);
        return ApiResponse.success("Questions reordered");
    }

    @Operation(summary = "Delete a reflection question")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        questionService.delete(id);
        return ApiResponse.success("Question deleted");
    }
}
