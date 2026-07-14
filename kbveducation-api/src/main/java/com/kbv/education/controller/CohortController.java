package com.kbv.education.controller;

import com.kbv.education.dto.request.CreateCohortRequest;
import com.kbv.education.dto.request.UpdateCohortRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.CohortResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.enums.CohortStatus;
import com.kbv.education.service.CohortService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Cohorts", description = "Cohort management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/cohorts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CohortController {

    private final CohortService cohortService;

    @Operation(summary = "List cohorts with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<CohortResponse>> list(
            @RequestParam(required = false) CohortStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(cohortService.list(status, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a cohort by id")
    @GetMapping("/{id}")
    public ApiResponse<CohortResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(cohortService.get(id));
    }

    @Operation(summary = "Create a cohort")
    @PostMapping
    public ResponseEntity<ApiResponse<CohortResponse>> create(@Valid @RequestBody CreateCohortRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cohort created", cohortService.create(request)));
    }

    @Operation(summary = "Update a cohort")
    @PutMapping("/{id}")
    public ApiResponse<CohortResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateCohortRequest request) {
        return ApiResponse.success("Cohort updated", cohortService.update(id, request));
    }

    @Operation(summary = "Archive (soft-delete) a cohort")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> archive(@PathVariable UUID id) {
        cohortService.archive(id);
        return ApiResponse.success("Cohort archived");
    }

    @Operation(summary = "List students in a cohort")
    @GetMapping("/{id}/students")
    public ApiResponse<List<StudentResponse>> listStudents(@PathVariable UUID id) {
        return ApiResponse.success(cohortService.listStudents(id));
    }

    @Operation(summary = "Assign a student to a cohort")
    @PostMapping("/{id}/students/{studentId}")
    public ApiResponse<CohortResponse> assignStudent(@PathVariable UUID id, @PathVariable UUID studentId) {
        return ApiResponse.success("Student assigned", cohortService.assignStudent(id, studentId));
    }

    @Operation(summary = "Remove a student from a cohort")
    @DeleteMapping("/{id}/students/{studentId}")
    public ApiResponse<Void> removeStudent(@PathVariable UUID id, @PathVariable UUID studentId) {
        cohortService.removeStudent(id, studentId);
        return ApiResponse.success("Student removed from cohort");
    }
}
