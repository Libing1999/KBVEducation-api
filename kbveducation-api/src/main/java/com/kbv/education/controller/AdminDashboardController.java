package com.kbv.education.controller;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.AdminDashboardTrendsResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Dashboard", description = "Aggregated admin metrics (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get admin dashboard metrics and recent activity")
    @GetMapping
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.success(dashboardService.adminDashboard());
    }

    @Operation(summary = "Get sparkline/chart time series for the admin dashboard (growth, daily activity, cohort status, top students)")
    @GetMapping("/trends")
    public ApiResponse<AdminDashboardTrendsResponse> trends(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(dashboardService.adminDashboardTrends(days));
    }
}
