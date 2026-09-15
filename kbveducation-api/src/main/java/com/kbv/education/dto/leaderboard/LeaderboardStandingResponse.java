package com.kbv.education.dto.leaderboard;

import java.util.List;

/**
 * The authenticated student's cohort standing — privacy-safe by
 * construction: the server never assembles anything beyond the top-N public
 * entries plus the caller's own entry, so there is no full-ranking payload
 * for a client to request more of, modify, or otherwise extract.
 */
public record LeaderboardStandingResponse(
        List<LeaderboardEntryResponse> topEntries,
        LeaderboardEntryResponse ownEntry,
        int totalStudents,
        int topN
) {
}
