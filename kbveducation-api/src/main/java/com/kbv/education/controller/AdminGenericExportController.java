package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.export.ExportDatasetMetadataResponse;
import com.kbv.education.dto.export.ExportFormat;
import com.kbv.education.dto.export.ExportHistoryResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.entity.enums.ExportDataset;
import com.kbv.education.repository.ExportHistoryRepository;
import com.kbv.education.repository.UserRepository;
import com.kbv.education.service.export.ExportFilters;
import com.kbv.education.service.export.GenericExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Export", description = "Generic, registry-driven CSV/Excel export for 10 datasets (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminGenericExportController {

    private final GenericExportService genericExportService;
    private final ExportHistoryRepository exportHistoryRepository;
    private final UserRepository userRepository;

    @Operation(summary = "List exportable datasets and which filters each one supports")
    @GetMapping("/datasets")
    public ApiResponse<List<ExportDatasetMetadataResponse>> datasets() {
        List<ExportDatasetMetadataResponse> metadata = genericExportService.listDatasets().stream()
                .map(m -> new ExportDatasetMetadataResponse(m.dataset(), m.label(), m.supportedFilters()))
                .toList();
        return ApiResponse.success(metadata);
    }

    @Operation(summary = "Export a dataset as CSV or XLSX, with optional date/cohort/student/status filters")
    @GetMapping("/dataset/{dataset}")
    public ResponseEntity<Resource> export(
            @PathVariable ExportDataset dataset,
            @RequestParam(defaultValue = "CSV") ExportFormat format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) String status) {
        ExportFilters filters = new ExportFilters(from, to, cohortId, studentId, status);
        return FileDownloads.attachment(genericExportService.export(dataset, format, filters));
    }

    @Operation(summary = "Recent export runs (old and new datasets alike) — powers the admin dashboard's Today's Exports card")
    @GetMapping("/history")
    public ApiResponse<List<ExportHistoryResponse>> history() {
        List<ExportHistoryResponse> history = exportHistoryRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(h -> new ExportHistoryResponse(
                        h.getId(), h.getDataset(), h.getFormat(), h.getRowCount(),
                        h.getCreatedBy() != null
                                ? userRepository.findByIdAndDeletedFalse(h.getCreatedBy()).map(u -> u.getFullName()).orElse("Unknown")
                                : "System",
                        h.getCreatedAt()))
                .toList();
        return ApiResponse.success(history);
    }
}
