package com.kbv.education.controller;

import com.kbv.education.dto.ai.AiSettingsResponse;
import com.kbv.education.dto.ai.AiStatusResponse;
import com.kbv.education.dto.ai.MorningDashboardResponse;
import com.kbv.education.dto.ai.ReferencePackVersionResponse;
import com.kbv.education.dto.ai.SaveReferencePackFileRequest;
import com.kbv.education.dto.ai.UpdateAiSettingsRequest;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.exception.BusinessRuleException;
import com.kbv.education.service.ai.AiEngine;
import com.kbv.education.service.ai.AiReviewService;
import com.kbv.education.service.ai.AiSettingsService;
import com.kbv.education.service.ai.MorningDashboardService;
import com.kbv.education.service.ai.ReferencePackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** AI settings, status, manual run and the reference pack (SUPER_ADMIN only). */
@Tag(name = "Admin — AI", description = "AI settings, reference pack and runs (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAiController {

    private final AiSettingsService settingsService;
    private final AiReviewService reviewService;
    private final ReferencePackService referencePackService;
    private final AiEngine engine;
    private final MorningDashboardService morningDashboardService;

    @Operation(summary = "Get AI settings (keys are never returned, only whether each is set up)")
    @GetMapping("/settings")
    public ApiResponse<AiSettingsResponse> getSettings() {
        return ApiResponse.success(settingsService.get());
    }

    @Operation(summary = "Update AI settings")
    @PutMapping("/settings")
    public ApiResponse<AiSettingsResponse> updateSettings(@Valid @RequestBody UpdateAiSettingsRequest request) {
        return ApiResponse.success("AI settings saved", settingsService.update(request));
    }

    @Operation(summary = "AI status: providers set up, running, waiting jobs, this month's usage")
    @GetMapping("/status")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.success(reviewService.status());
    }

    @Operation(summary = "Morning dashboard for one day (default: yesterday in the AI time zone)")
    @GetMapping("/morning")
    public ApiResponse<MorningDashboardResponse> morning(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(morningDashboardService.build(date));
    }

    @Operation(summary = "Start tonight's run now (in the background)")
    @PostMapping("/run-now")
    public ApiResponse<Void> runNow() {
        if (!settingsService.getActive().isAiEnabled()) {
            throw new BusinessRuleException("Switch the AI on in Settings first");
        }
        if (!engine.startRunNow()) {
            throw new BusinessRuleException("An AI run is already in progress");
        }
        return ApiResponse.success("AI run started", null);
    }

    // ------------------------------------------------------------ reference pack

    @Operation(summary = "List all reference pack versions, newest first")
    @GetMapping("/reference-pack/versions")
    public ApiResponse<List<ReferencePackVersionResponse>> versions() {
        return ApiResponse.success(referencePackService.listVersions());
    }

    @Operation(summary = "The active reference pack with its files (null when none uploaded yet)")
    @GetMapping("/reference-pack/active")
    public ApiResponse<ReferencePackVersionResponse> active() {
        return ApiResponse.success(referencePackService.getActive());
    }

    @Operation(summary = "One reference pack version with its files")
    @GetMapping("/reference-pack/versions/{id}")
    public ApiResponse<ReferencePackVersionResponse> version(@PathVariable UUID id) {
        return ApiResponse.success(referencePackService.getVersion(id));
    }

    @Operation(summary = "Upload Markdown files (same name replaces); creates a new version")
    @PostMapping(value = "/reference-pack/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReferencePackVersionResponse> upload(@RequestPart("files") MultipartFile[] files,
                                                            @RequestParam(required = false) String note) {
        return ApiResponse.success("Reference pack updated", referencePackService.upload(files, note));
    }

    @Operation(summary = "Create or edit one file; creates a new version")
    @PutMapping("/reference-pack/files")
    public ApiResponse<ReferencePackVersionResponse> saveFile(@Valid @RequestBody SaveReferencePackFileRequest request) {
        return ApiResponse.success("Reference pack updated",
                referencePackService.saveFile(request.fileName(), request.content(), request.note()));
    }

    @Operation(summary = "Delete one file; creates a new version")
    @DeleteMapping("/reference-pack/files/{fileName}")
    public ApiResponse<ReferencePackVersionResponse> deleteFile(@PathVariable String fileName,
                                                                @RequestParam(required = false) String note) {
        return ApiResponse.success("Reference pack updated", referencePackService.deleteFile(fileName, note));
    }

    @Operation(summary = "Make an older (or newer) version the active one; applies from the next run")
    @PostMapping("/reference-pack/versions/{id}/activate")
    public ApiResponse<ReferencePackVersionResponse> activate(@PathVariable UUID id) {
        return ApiResponse.success("Reference pack version activated", referencePackService.activate(id));
    }
}
