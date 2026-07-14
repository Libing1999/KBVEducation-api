package com.kbv.education.controller;

import com.kbv.education.dto.audit.ScoreAuditLogResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.ScoreAuditEntityType;
import com.kbv.education.service.ScoreAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Audit Logs", description = "Score-related change history (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminScoreAuditLogController {

    private final ScoreAuditLogService scoreAuditLogService;

    @Operation(summary = "List score-related audit log entries, newest first")
    @GetMapping
    public ApiResponse<PageResponse<ScoreAuditLogResponse>> list(
            @RequestParam(required = false) ScoreAuditEntityType entityType,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(scoreAuditLogService.list(entityType, studentId, page, size));
    }
}
