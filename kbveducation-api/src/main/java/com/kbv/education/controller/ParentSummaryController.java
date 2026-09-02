package com.kbv.education.controller;

import com.kbv.education.dto.parent.ParentChildResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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

    @Operation(summary = "Get the weekly summary for one of the current parent's linked students")
    @GetMapping
    public ApiResponse<ParentSummaryResponse> summary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId) {
        return ApiResponse.success(parentSummaryService.getSummary(principal.getId(), studentId));
    }

    @Operation(summary = "List the current parent's linked children")
    @GetMapping("/children")
    public ApiResponse<List<ParentChildResponse>> children(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(parentSummaryService.listMyChildren(principal.getId()));
    }
}
