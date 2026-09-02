package com.kbv.education.controller;

import com.kbv.education.dto.parent.ParentSummaryResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.ParentSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Parent screen's single weekly summary — replaces the parent's old 6-tab
 * nav (Dashboard/My Lessons/Activity/Calendar/Certificates/Profile) entirely.
 */
@Tag(name = "Parent — Summary", description = "The Parent screen's weekly summary")
@RestController
@RequestMapping("/api/parent/summary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARENT')")
public class ParentSummaryController {

    private final ParentSummaryService parentSummaryService;

    @Operation(summary = "Get the weekly summary for the current parent's linked student")
    @GetMapping
    public ApiResponse<ParentSummaryResponse> summary(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(parentSummaryService.getSummary(principal.getId()));
    }
}
