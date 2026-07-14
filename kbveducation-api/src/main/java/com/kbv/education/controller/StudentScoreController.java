package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.ScoreEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Student — Score", description = "Composite score for the authenticated student (STUDENT only)")
@RestController
@RequestMapping("/api/student/score")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentScoreController {

    private final ScoreEngineService scoreEngineService;

    @Operation(summary = "Get my current composite score")
    @GetMapping
    public ApiResponse<StudentScoreResponse> myScore(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(scoreEngineService.getCurrent(principal.getId()));
    }

    @Operation(summary = "Get my score calculation history, newest first")
    @GetMapping("/history")
    public ApiResponse<PageResponse<StudentScoreResponse>> history(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(scoreEngineService.getHistory(principal.getId(), page, size));
    }
}
