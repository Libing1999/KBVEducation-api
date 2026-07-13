package com.kbv.education.controller;

import com.kbv.education.dto.request.UpdateUserRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.UserResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile for any authenticated user. Students and admins may
 * update their own profile; parents may view but not edit (enforced below).
 */
@Tag(name = "Profile", description = "Current user's profile")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @Operation(summary = "Get the current user's profile")
    @GetMapping
    public ApiResponse<UserResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(userService.get(principal.getId()));
    }

    @Operation(summary = "Update the current user's profile (students and admins only)")
    @PreAuthorize("hasAnyRole('STUDENT', 'SUPER_ADMIN')")
    @PutMapping
    public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("Profile updated", userService.update(principal.getId(), request));
    }
}
