package com.kbv.education.controller;

import com.kbv.education.dto.audit.AuditTrailResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * The general-purpose, cross-cutting audit trail (Phase 5 Step 4) — distinct
 * from the existing score/tier-scoped log already served at
 * {@code /api/admin/audit-logs} by {@code AdminScoreAuditLogController},
 * which this deliberately does not touch or rename.
 */
@Tag(name = "Admin — Audit Trail", description = "Cross-cutting audit trail: login, CRUD, certificates, exports (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/audit-trail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuditTrailController {

    private final AuditLogService auditLogService;

    @Operation(summary = "List audit trail entries, filterable by actor/action/entity type/date range")
    @GetMapping
    public ApiResponse<PageResponse<AuditTrailResponse>> list(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(auditLogService.list(actorId, action, entityType, from, to, page, size));
    }

    @Operation(summary = "Count of audit events recorded today — powers the admin dashboard's Audit Events Today card")
    @GetMapping("/today-count")
    public ApiResponse<Long> todayCount() {
        return ApiResponse.success(auditLogService.countToday());
    }
}
