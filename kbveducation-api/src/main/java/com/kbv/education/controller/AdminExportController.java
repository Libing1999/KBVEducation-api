package com.kbv.education.controller;

import com.kbv.education.dto.export.ExportFormat;
import com.kbv.education.entity.enums.LeaderboardSortField;
import com.kbv.education.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.kbv.education.controller.support.FileDownloads.attachment;

@Tag(name = "Admin — Export", description = "CSV/Excel exports of leaderboard, scores, tiers and student progress (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminExportController {

    private final ExportService exportService;

    @Operation(summary = "Export a cohort's leaderboard")
    @GetMapping("/leaderboard")
    public ResponseEntity<Resource> leaderboard(@RequestParam UUID cohortId,
                                                 @RequestParam(required = false) LeaderboardSortField sortBy,
                                                 @RequestParam(defaultValue = "CSV") ExportFormat format) {
        return attachment(exportService.exportLeaderboard(cohortId, sortBy, format));
    }

    @Operation(summary = "Export a cohort's current student scores")
    @GetMapping("/scores")
    public ResponseEntity<Resource> scores(@RequestParam UUID cohortId,
                                            @RequestParam(defaultValue = "CSV") ExportFormat format) {
        return attachment(exportService.exportScores(cohortId, format));
    }

    @Operation(summary = "Export a cohort's current tier decisions")
    @GetMapping("/tiers")
    public ResponseEntity<Resource> tiers(@RequestParam UUID cohortId,
                                           @RequestParam(defaultValue = "CSV") ExportFormat format) {
        return attachment(exportService.exportTiers(cohortId, format));
    }

    @Operation(summary = "Export a single student's progress")
    @GetMapping("/progress/{studentId}")
    public ResponseEntity<Resource> progress(@PathVariable UUID studentId,
                                              @RequestParam(defaultValue = "CSV") ExportFormat format) {
        return attachment(exportService.exportStudentProgress(studentId, format));
    }
}
