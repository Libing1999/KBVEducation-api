package com.kbv.education.controller;

import com.kbv.education.dto.applog.ApplicationLogResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LogSeverity;
import com.kbv.education.service.ApplicationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Application Logs", description = "Error/warning monitoring feed (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/application-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminApplicationLogController {

    private final ApplicationLogService applicationLogService;

    @Operation(summary = "List application error/warning log entries, newest first")
    @GetMapping
    public ApiResponse<PageResponse<ApplicationLogResponse>> list(
            @RequestParam(required = false) LogSeverity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(applicationLogService.list(severity, page, size));
    }
}
