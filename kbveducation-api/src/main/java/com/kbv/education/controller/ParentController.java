package com.kbv.education.controller;

import com.kbv.education.dto.request.CreateParentRequest;
import com.kbv.education.dto.request.LinkStudentRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.ParentResponse;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.service.ParentService;
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

@Tag(name = "Admin — Parents", description = "Parent management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/parents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ParentController {

    private final ParentService parentService;

    @Operation(summary = "List parents with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<ParentResponse>> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(parentService.list(status, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a parent by id")
    @GetMapping("/{id}")
    public ApiResponse<ParentResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(parentService.get(id));
    }

    @Operation(summary = "Create a parent (optionally linked to a student)")
    @PostMapping
    public ResponseEntity<ApiResponse<ParentResponse>> create(@Valid @RequestBody CreateParentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Parent created", parentService.create(request)));
    }

    @Operation(summary = "Update a parent's profile fields")
    @PutMapping("/{id}")
    public ApiResponse<ParentResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Parent updated", parentService.update(id, request));
    }

    @Operation(summary = "Link a parent to an additional student")
    @PostMapping("/{id}/student")
    public ApiResponse<ParentResponse> linkStudent(@PathVariable UUID id,
                                                   @Valid @RequestBody LinkStudentRequest request) {
        return ApiResponse.success("Student linked", parentService.linkStudent(id, request));
    }

    @Operation(summary = "Unlink one of a parent's students")
    @DeleteMapping("/{id}/student/{studentId}")
    public ApiResponse<Void> unlinkStudent(@PathVariable UUID id, @PathVariable UUID studentId) {
        parentService.unlinkStudent(id, studentId);
        return ApiResponse.success("Student unlinked");
    }

    @Operation(summary = "Soft-delete a parent")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        parentService.softDelete(id);
        return ApiResponse.success("Parent deleted");
    }
}
