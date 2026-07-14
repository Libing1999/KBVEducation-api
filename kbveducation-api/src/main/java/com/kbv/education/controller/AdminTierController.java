package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.tier.OverrideTierRequest;
import com.kbv.education.dto.tier.TierHistoryResponse;
import com.kbv.education.security.UserPrincipal;
import com.kbv.education.service.TierEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Tier", description = "Confirm or override a student's graduation tier (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/tier")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTierController {

    private final TierEngineService tierEngineService;

    @Operation(summary = "Confirm a student's calculated tier as-is")
    @PutMapping("/{studentId}/confirm")
    public ApiResponse<TierHistoryResponse> confirm(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable UUID studentId) {
        return ApiResponse.success("Tier confirmed", tierEngineService.confirm(studentId, principal.getId()));
    }

    @Operation(summary = "Override a student's tier (reason required)")
    @PutMapping("/{studentId}")
    public ApiResponse<TierHistoryResponse> override(@AuthenticationPrincipal UserPrincipal principal,
                                                      @PathVariable UUID studentId,
                                                      @Valid @RequestBody OverrideTierRequest request) {
        return ApiResponse.success("Tier overridden",
                tierEngineService.override(studentId, request.tierName(), request.reason(), principal.getId()));
    }

    @Operation(summary = "Get a student's tier decision history")
    @GetMapping("/{studentId}/history")
    public ApiResponse<PageResponse<TierHistoryResponse>> history(@PathVariable UUID studentId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tierEngineService.history(studentId, page, size));
    }
}
