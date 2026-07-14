package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Score dashboard for the authenticated student or parent. Admins use
 * {@code /api/admin/dashboard} instead.
 */
@Tag(name = "Dashboard", description = "Student/parent score dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get the score dashboard for the current student or parent")
    @GetMapping("/me")
    public ApiResponse<ScoreDashboardResponse> myDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        if (hasRole(principal, "STUDENT")) {
            return ApiResponse.success(dashboardService.studentDashboard(principal.getId()));
        }
        if (hasRole(principal, "PARENT")) {
            return ApiResponse.success(dashboardService.parentDashboard(principal.getId()));
        }
        throw new BadRequestException("This dashboard is available to students and parents only");
    }

    private boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
