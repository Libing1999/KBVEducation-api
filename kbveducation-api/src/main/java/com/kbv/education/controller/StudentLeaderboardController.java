package com.kbv.education.controller;

import com.kbv.education.dto.leaderboard.LeaderboardEntryResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Rankings and scores only — never another student's reflections, homework, or practice logs. */
@Tag(name = "Student — Leaderboard", description = "My cohort's leaderboard (STUDENT only)")
@RestController
@RequestMapping("/api/student/leaderboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentLeaderboardController {

    private final LeaderboardService leaderboardService;

    @Operation(summary = "Get my cohort's leaderboard")
    @GetMapping
    public ApiResponse<List<LeaderboardEntryResponse>> myLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) LeaderboardSortField sortBy) {
        return ApiResponse.success(leaderboardService.studentView(principal.getId(), sortBy));
    }
}
