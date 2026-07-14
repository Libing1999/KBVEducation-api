package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.tier.TierRuleResponse;
import com.kbv.education.dto.tier.UpsertTierRuleRequest;
import com.kbv.education.service.TierRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin — Tier Rules", description = "Configurable graduation tier thresholds (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/tier-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTierRuleController {

    private final TierRuleService tierRuleService;

    @Operation(summary = "List the configured tiers, ranked best-first")
    @GetMapping
    public ApiResponse<List<TierRuleResponse>> list() {
        return ApiResponse.success(tierRuleService.list());
    }

    @Operation(summary = "Update all tier rules (thresholds must not overlap)")
    @PutMapping
    public ApiResponse<List<TierRuleResponse>> updateAll(@Valid @RequestBody List<@Valid UpsertTierRuleRequest> rules) {
        return ApiResponse.success("Tier rules updated", tierRuleService.updateAll(rules));
    }
}
