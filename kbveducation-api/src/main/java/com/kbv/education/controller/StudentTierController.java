package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.tier.CurrentTierResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.TierEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Student — Tier", description = "Graduation tier for the authenticated student (STUDENT only)")
@RestController
@RequestMapping("/api/student/tier")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentTierController {

    private final TierEngineService tierEngineService;

    @Operation(summary = "Get my current tier: calculated, confirmed, next tier, and what's left to reach it")
    @GetMapping
    public ApiResponse<CurrentTierResponse> myTier(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(tierEngineService.getCurrentTier(principal.getId()));
    }
}
