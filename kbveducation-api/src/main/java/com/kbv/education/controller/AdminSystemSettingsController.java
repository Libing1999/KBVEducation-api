package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.settings.SystemSettingsResponse;
import com.kbv.education.dto.settings.UpdateSystemSettingsRequest;
import com.kbv.education.service.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — System Settings", description = "Branding, locale, uploads, security policy, feature toggles (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @Operation(summary = "Get the active system settings")
    @GetMapping
    public ApiResponse<SystemSettingsResponse> get() {
        return ApiResponse.success(systemSettingsService.getActive());
    }

    @Operation(summary = "Update the system settings")
    @PutMapping
    public ApiResponse<SystemSettingsResponse> update(@Valid @RequestBody UpdateSystemSettingsRequest request) {
        return ApiResponse.success("Settings updated", systemSettingsService.update(request));
    }
}
