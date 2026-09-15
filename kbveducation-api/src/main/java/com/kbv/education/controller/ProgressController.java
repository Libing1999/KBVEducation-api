package com.kbv.education.controller;

import com.kbv.education.dto.dashboard.ActivityLogResponse;
import com.kbv.education.dto.dashboard.StudentProgressResponse;
import com.kbv.education.dto.dashboard.StudyDayResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.ActivityService;
import com.kbv.education.service.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Progress, activity timeline and calendar for the current student or parent. */
@Tag(name = "Dashboard — Progress", description = "Student/parent progress, timeline and calendar")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'PARENT')")
public class ProgressController {

    private final ProgressService progressService;
    private final ActivityService activityService;

    @Operation(summary = "Get my progress statistics (current month + course total)")
    @GetMapping("/progress")
    public ApiResponse<StudentProgressResponse> progress(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId) {
        return ApiResponse.success(progressService.getProgress(principal.getId(), studentId));
    }

    @Operation(summary = "Get my activity timeline (newest first)")
    @GetMapping("/activity")
    public ApiResponse<PageResponse<ActivityLogResponse>> activity(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                activityService.list(progressService.resolveStudentId(principal.getId(), studentId), page, size));
    }

    @Operation(summary = "Get my activity calendar for a date range (defaults to the current month)")
    @GetMapping("/calendar")
    public ApiResponse<List<StudyDayResponse>> calendar(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.plusMonths(1).minusDays(1);
        return ApiResponse.success(
                activityService.calendar(progressService.resolveStudentId(principal.getId(), studentId), start, end));
    }
}
