package com.kbv.education.controller;

import com.kbv.education.dto.cohortday.CohortDayResponse;
import com.kbv.education.dto.cohortday.UpsertCohortDayRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.CohortDayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Cohort Days", description = "Classify a cohort's dates as Lesson/Rest/Skip Day (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/cohorts/{cohortId}/days")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCohortDayController {

    private final CohortDayService cohortDayService;

    @Operation(summary = "List a cohort's day classifications over a date range (unconfigured dates default to LESSON_DAY)")
    @GetMapping
    public ApiResponse<List<CohortDayResponse>> list(
            @PathVariable UUID cohortId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(cohortDayService.list(cohortId, from, to));
    }

    @Operation(summary = "Set a date's classification for this cohort")
    @PutMapping
    public ApiResponse<CohortDayResponse> upsert(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cohortId,
            @Valid @RequestBody UpsertCohortDayRequest request) {
        return ApiResponse.success("Day classification saved",
                cohortDayService.upsert(cohortId, request.date(), request.dayType(), principal.getId()));
    }

    @Operation(summary = "Remove a date's override, reverting it to the default (LESSON_DAY)")
    @DeleteMapping("/{date}")
    public ApiResponse<Void> reset(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID cohortId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        cohortDayService.reset(cohortId, date, principal.getId());
        return ApiResponse.success("Day classification reset");
    }
}
