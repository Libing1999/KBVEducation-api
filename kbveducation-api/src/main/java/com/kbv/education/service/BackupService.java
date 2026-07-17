package com.kbv.education.service;

import com.kbv.education.dto.backup.BackupHistoryResponse;
import com.kbv.education.dto.file.FileDownloadResult;

import java.util.List;
import java.util.UUID;

public interface BackupService {

    /** Runs pg_dump synchronously (admin-only, rare, unscheduled action) and records the outcome either way. */
    BackupHistoryResponse create();

    List<BackupHistoryResponse> list();

    FileDownloadResult download(UUID id);

    /** Removes both the dump file on disk and its history record. */
    void delete(UUID id);
}
