package com.kbv.education.controller;

import com.kbv.education.dto.analytics.PillarAnalyticsResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.PracticeStatus;
import com.kbv.education.service.analytics.PillarAnalyticsService;
import com.kbv.education.service.analytics.PillarAnalyticsService.Pillar;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The Analytics screen (memo "AI plan" 17.10.2026, section 2): graphs and tables per pillar. */
@Tag(name = "Admin — Pillar analytics", description = "Practice, Reflection, Homework and Quiz graphs (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/pillar-analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPillarAnalyticsController {

    private final PillarAnalyticsService service;

    @Operation(summary = "Graphs and tables for one pillar. Who: studentIds, else cohortId, else the whole class")
    @GetMapping("/{pillar}")
    public ApiResponse<PillarAnalyticsResponse> get(
            @PathVariable String pillar,
            @RequestParam(required = false) List<UUID> studentIds,
            @RequestParam(required = false) UUID cohortId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PracticeStatus status) {
        Pillar p = Pillar.valueOf(pillar.toUpperCase(Locale.ROOT));
        PillarAnalyticsService.Query q = new PillarAnalyticsService.Query(
                studentIds == null ? null : new LinkedHashSet<>(studentIds), cohortId, from, to, status);
        return ApiResponse.success(service.build(p, q));
    }
}
