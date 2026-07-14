package com.kbv.education.controller;

import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.dto.scoreconfig.ScoreConfigResponse;
import com.kbv.education.dto.scoreconfig.UpdateScoreConfigRequest;
import com.kbv.education.service.ScoreConfigService;
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

@Tag(name = "Admin — Score Config", description = "Configurable score-engine weights and windows (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/score-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminScoreConfigController {

    private final ScoreConfigService scoreConfigService;

    @Operation(summary = "Get the active score configuration")
    @GetMapping
    public ApiResponse<ScoreConfigResponse> getActive() {
        return ApiResponse.success(scoreConfigService.getActive());
    }

    @Operation(summary = "Update the score configuration (weights must total 100%)")
    @PutMapping
    public ApiResponse<ScoreConfigResponse> update(@Valid @RequestBody UpdateScoreConfigRequest request) {
        return ApiResponse.success("Score configuration updated", scoreConfigService.update(request));
    }
}
