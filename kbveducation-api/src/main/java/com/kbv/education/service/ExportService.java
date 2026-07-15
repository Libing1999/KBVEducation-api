package com.kbv.education.service;

import com.kbv.education.dto.export.ExportFormat;
import com.kbv.education.dto.file.FileDownloadResult;
import com.kbv.education.entity.enums.LeaderboardSortField;

import java.util.UUID;

/** Builds CSV/XLSX exports from data already computed by the Steps 3-6 score/tier/leaderboard/analytics engines. */
public interface ExportService {

    FileDownloadResult exportLeaderboard(UUID cohortId, LeaderboardSortField sortBy, ExportFormat format);

    FileDownloadResult exportScores(UUID cohortId, ExportFormat format);

    FileDownloadResult exportTiers(UUID cohortId, ExportFormat format);

    FileDownloadResult exportStudentProgress(UUID studentId, ExportFormat format);
}
