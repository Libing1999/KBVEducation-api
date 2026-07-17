package com.kbv.education.controller;

import com.kbv.education.controller.support.FileDownloads;
import com.kbv.education.dto.backup.BackupHistoryResponse;
import com.kbv.education.dto.response.ApiResponse;
import com.kbv.education.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin — Backups", description = "Manual database backups, no scheduling (SUPER_ADMIN only)")
@RestController
@RequestMapping("/api/admin/backups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBackupController {

    private final BackupService backupService;

    @Operation(summary = "Create a new database backup (runs pg_dump synchronously)")
    @PostMapping
    public ApiResponse<BackupHistoryResponse> create() {
        return ApiResponse.success("Backup created", backupService.create());
    }

    @Operation(summary = "List backup history")
    @GetMapping
    public ApiResponse<List<BackupHistoryResponse>> list() {
        return ApiResponse.success(backupService.list());
    }

    @Operation(summary = "Download a backup file")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        return FileDownloads.attachment(backupService.download(id));
    }

    @Operation(summary = "Delete a backup (removes both the file and its history record)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        backupService.delete(id);
        return ApiResponse.success("Backup deleted");
    }
}
