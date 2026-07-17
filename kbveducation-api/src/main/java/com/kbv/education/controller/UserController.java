package com.kbv.education.controller;

import com.kbv.education.dto.request.CreateUserRequest;
import com.kbv.education.dto.request.ResetPasswordRequest;
import com.kbv.education.dto.request.UpdateStatusRequest;
import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.entity.enums.RoleType;
import com.kbv.education.entity.enums.UserStatus;
import com.kbv.education.service.UserService;
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

@Tag(name = "Admin — Users", description = "User management (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    @Operation(summary = "List users with pagination, filtering, and search")
    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) RoleType role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success(userService.list(role, status, search, page, size, sort, direction));
    }

    @Operation(summary = "Get a user by id")
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(userService.get(id));
    }

    @Operation(summary = "Create a user")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created", userService.create(request)));
    }

    @Operation(summary = "Update a user's profile fields")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated", userService.update(id, request));
    }

    @Operation(summary = "Activate or deactivate a user")
    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.success("User status updated", userService.updateStatus(id, request));
    }

    @Operation(summary = "Reset a user's password")
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable UUID id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ApiResponse.success("Password reset successfully");
    }

    @Operation(summary = "Soft-delete a user")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userService.softDelete(id);
        return ApiResponse.success("User deleted");
    }

    @Operation(summary = "Clear a user's account lockout")
    @PutMapping("/{id}/unlock")
    public ApiResponse<UserResponse> unlock(@PathVariable UUID id) {
        return ApiResponse.success("Account unlocked", userService.unlock(id));
    }
}
