package com.kbv.education.dto.response;

import java.util.List;

/** Aggregated metrics + recent activity for the admin dashboard. */
public record AdminDashboardResponse(
        long totalStudents,
        long totalParents,
        long totalCohorts,
        long activeCohorts,
        long inactiveCohorts,
        long todaysLogins,
        long lockedAccounts,
        boolean systemHealthy,
        long freeDiskSpaceMb,
        List<UserResponse> recentUsers,
        List<CohortResponse> recentCohorts
) {
}
