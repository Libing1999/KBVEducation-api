package com.kbv.education.controller;

import com.kbv.education.dto.common.ReorderRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.subject.SubjectRequest;
import com.kbv.education.dto.subject.SubjectResponse;
import com.kbv.education.service.SubjectService;
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

@Tag(name = "Admin — Subjects", description = "Configure the practice-log Subject dropdown (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SubjectController {

    private final SubjectService subjectService;

    @Operation(summary = "List all subjects")
    @GetMapping
    public ApiResponse<List<SubjectResponse>> list() {
        return ApiResponse.success(subjectService.listAll());
    }

    @Operation(summary = "Create a subject")
    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subject created", subjectService.create(request)));
    }

    @Operation(summary = "Update a subject")
    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> update(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.success("Subject updated", subjectService.update(id, request));
    }

    @Operation(summary = "Enable or disable a subject")
    @PatchMapping("/{id}/enabled")
    public ApiResponse<SubjectResponse> setEnabled(@PathVariable UUID id, @RequestParam boolean enabled) {
        return ApiResponse.success(subjectService.setEnabled(id, enabled));
    }

    @Operation(summary = "Reorder subjects")
    @PatchMapping("/reorder")
    public ApiResponse<Void> reorder(@Valid @RequestBody ReorderRequest request) {
        subjectService.reorder(request);
        return ApiResponse.success("Subjects reordered");
    }

    @Operation(summary = "Delete a subject (only if unused)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        subjectService.delete(id);
        return ApiResponse.success("Subject deleted");
    }
}
