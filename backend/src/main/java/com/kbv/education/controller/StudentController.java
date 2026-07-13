package com.kbv.education.controller;

import com.kbv.education.dto.request.AssignCohortRequest;
import com.kbv.education.dto.request.CreateStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.StudentResponse;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.service.StudentService;
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

import java.util.UUID;

@Tag(name = "Admin — Students", description = "Student management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "List students with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<StudentResponse>> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(studentService.list(status, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a student by id")
    @GetMapping("/{id}")
    public ApiResponse<StudentResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(studentService.get(id));
    }

    @Operation(summary = "Create a student (optionally assigned to a cohort)")
    @PostMapping
    public ResponseEntity<ApiResponse<StudentResponse>> create(@Valid @RequestBody CreateStudentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student created", studentService.create(request)));
    }

    @Operation(summary = "Update a student's profile fields")
    @PutMapping("/{id}")
    public ApiResponse<StudentResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Student updated", studentService.update(id, request));
    }

    @Operation(summary = "Assign a student to a cohort")
    @PostMapping("/{id}/cohort")
    public ApiResponse<StudentResponse> assignCohort(@PathVariable UUID id,
                                                     @Valid @RequestBody AssignCohortRequest request) {
        return ApiResponse.success("Student assigned to cohort", studentService.assignCohort(id, request));
    }

    @Operation(summary = "Remove a student from their active cohort")
    @DeleteMapping("/{id}/cohort")
    public ApiResponse<Void> removeFromCohort(@PathVariable UUID id) {
        studentService.removeFromCohort(id);
        return ApiResponse.success("Student removed from cohort");
    }

    @Operation(summary = "Soft-delete a student")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        studentService.softDelete(id);
        return ApiResponse.success("Student deleted");
    }
}
