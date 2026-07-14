package com.kbv.education.controller;

import com.kbv.education.dto.dashboard.ActivityLogResponse;
import com.kbv.education.dto.dashboard.AdminStatisticsResponse;
import com.kbv.education.dto.dashboard.StudentProgressResponse;
import com.kbv.education.dto.dashboard.StudyDayResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.service.ActivityService;
import com.kbv.education.service.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Admin activity statistics and per-student drill-down (SUPER_ADMIN only). */
@Tag(name = "Admin — Statistics", description = "Activity statistics and per-student progress (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminStatisticsController {

    private final ProgressService progressService;
    private final ActivityService activityService;

    @Operation(summary = "Activity statistics cards")
    @GetMapping("/statistics")
    public ApiResponse<AdminStatisticsResponse> statistics() {
        return ApiResponse.success(progressService.adminStatistics());
    }

    @Operation(summary = "A student's progress")
    @GetMapping("/students/{studentId}/progress")
    public ApiResponse<StudentProgressResponse> studentProgress(@PathVariable UUID studentId) {
        return ApiResponse.success(progressService.getProgressForStudent(studentId));
    }

    @Operation(summary = "A student's activity timeline")
    @GetMapping("/students/{studentId}/activity")
    public ApiResponse<PageResponse<ActivityLogResponse>> studentActivity(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(activityService.list(studentId, page, size));
    }

    @Operation(summary = "A student's activity calendar")
    @GetMapping("/students/{studentId}/calendar")
    public ApiResponse<List<StudyDayResponse>> studentCalendar(
            @PathVariable UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : start.plusMonths(1).minusDays(1);
        return ApiResponse.success(activityService.calendar(studentId, start, end));
    }
}
