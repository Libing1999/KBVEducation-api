package com.kbv.education.controller;

import com.kbv.education.dto.leaderboard.LeaderboardStandingResponse;
import com.kbv.education.dto.message.StudentMessageResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.CoachMessageService;
import com.kbv.education.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Rankings, scores, and coach messages only — never another student's reflections, homework, or practice logs. */
@Tag(name = "Student — Leaderboard", description = "My cohort's leaderboard (STUDENT only)")
@RestController
@RequestMapping("/api/student/leaderboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentLeaderboardController {

    private final LeaderboardService leaderboardService;
    private final CoachMessageService coachMessageService;

    @Operation(summary = "Get my cohort standing (top-N public entries + my own position only)")
    @GetMapping
    public ApiResponse<LeaderboardStandingResponse> myLeaderboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) LeaderboardSortField sortBy) {
        return ApiResponse.success(leaderboardService.studentView(principal.getId(), sortBy));
    }

    @Operation(summary = "Get my Live Action messages (individual + my cohort's collective notes), newest first")
    @GetMapping("/messages")
    public ApiResponse<List<StudentMessageResponse>> myMessages(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(coachMessageService.listForStudent(principal.getId()));
    }

    @Operation(summary = "Mark one of my Live Action messages as read")
    @PostMapping("/messages/{id}/read")
    public ApiResponse<Void> markMessageRead(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID id) {
        coachMessageService.markReadForStudent(principal.getId(), id);
        return ApiResponse.success("Marked as read");
    }
}
