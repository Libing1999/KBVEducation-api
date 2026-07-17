package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.settings.PublicSettingsResponse;
import com.kbv.education.service.SystemSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated-safe settings (branding + maintenance mode) for the login page and app bootstrap. */
@Tag(name = "Public Settings", description = "Unauthenticated-safe branding/maintenance-mode bootstrap")
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class PublicSettingsController {

    private final SystemSettingsService systemSettingsService;

    @Operation(summary = "Get public-safe branding and maintenance-mode status")
    @GetMapping("/public")
    public ApiResponse<PublicSettingsResponse> getPublic() {
        return ApiResponse.success(systemSettingsService.getPublic());
    }
}
