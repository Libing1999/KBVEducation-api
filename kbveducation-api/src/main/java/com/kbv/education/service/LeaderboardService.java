package com.kbv.education.service;

import com.kbv.education.dto.leaderboard.LeaderboardEntryResponse;
import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.entity.enums.LeaderboardSortField;

import java.util.List;
import java.util.UUID;

public interface LeaderboardService {

    /** Rebuilds the cached leaderboard for a cohort across all 5 sort fields. No-op if the leaderboard is disabled. */
    void regenerate(UUID cohortId);

    /** Regenerates the leaderboard for the cohort a student currently belongs to (no-op if unassigned). */
    void regenerateForStudent(UUID studentId);

    /** Regenerates every active cohort's leaderboard. */
    void regenerateAll();

    PageResponse<LeaderboardEntryResponse> adminList(UUID cohortId, LeaderboardSortField sortBy, int page, int size);

    /** The authenticated student's own cohort leaderboard. Throws if disabled or the student has no cohort. */
    List<LeaderboardEntryResponse> studentView(UUID studentId, LeaderboardSortField sortBy);
}
