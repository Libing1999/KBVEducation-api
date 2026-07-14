package com.kbv.education.controller;

import com.kbv.education.dto.leaderboard.LeaderboardEntryResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Leaderboard", description = "Per-cohort leaderboard (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/leaderboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminLeaderboardController {

    private final LeaderboardService leaderboardService;

    @Operation(summary = "List a cohort's leaderboard, ranked")
    @GetMapping
    public ApiResponse<PageResponse<LeaderboardEntryResponse>> list(
            @RequestParam UUID cohortId,
            @RequestParam(required = false) LeaderboardSortField sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(leaderboardService.adminList(cohortId, sortBy, page, size));
    }

    @Operation(summary = "Manually regenerate a cohort's leaderboard cache")
    @PostMapping("/regenerate")
    public ApiResponse<Void> regenerate(@RequestParam UUID cohortId) {
        leaderboardService.regenerate(cohortId);
        return ApiResponse.success("Leaderboard regenerated");
    }
}
