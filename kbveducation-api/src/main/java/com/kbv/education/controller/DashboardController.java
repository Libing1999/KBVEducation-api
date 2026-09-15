package com.kbv.education.controller;

import com.kbv.education.dto.dashboard.StudentProgressResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.exception.BadRequestException;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.DashboardService;
import com.kbv.education.service.ProgressService;
import com.kbv.education.service.ScoreEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
    private final ScoreEngineService scoreEngineService;
    private final ProgressService progressService;

    @Operation(summary = "Get the score dashboard for the current student or parent")
    @GetMapping("/me")
    public ApiResponse<ScoreDashboardResponse> myDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId) {
        if (hasRole(principal, "STUDENT")) {
            return ApiResponse.success(dashboardService.studentDashboard(principal.getId()));
        }
        if (hasRole(principal, "PARENT")) {
            return ApiResponse.success(dashboardService.parentDashboard(principal.getId(), studentId));
        }
        throw new BadRequestException("This dashboard is available to students and parents only");
    }

    @Operation(summary = "Get the composite score for the current student, or a parent's linked student")
    @GetMapping("/composite")
    public ApiResponse<StudentScoreResponse> composite(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId) {
        return ApiResponse.success(
                scoreEngineService.getCurrent(progressService.resolveStudentId(principal.getId(), studentId)));
    }

    @Operation(summary = "Get progress statistics for the current student, or a parent's linked student")
    @GetMapping("/statistics")
    public ApiResponse<StudentProgressResponse> statistics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId) {
        return ApiResponse.success(progressService.getProgress(principal.getId(), studentId));
    }

    private boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
