package com.kbv.education.controller;

import com.kbv.education.dto.analytics.AdminAnalyticsResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Analytics", description = "Aggregate score/tier analytics (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get aggregate analytics (all cohorts combined, or one cohort if cohortId is given)")
    @GetMapping
    public ApiResponse<AdminAnalyticsResponse> get(@RequestParam(required = false) UUID cohortId) {
        return ApiResponse.success(analyticsService.get(cohortId));
    }
}
