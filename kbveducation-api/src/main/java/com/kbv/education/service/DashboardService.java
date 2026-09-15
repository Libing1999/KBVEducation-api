package com.kbv.education.service;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.AdminDashboardTrendsResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;

import java.util.UUID;

public interface DashboardService {

    AdminDashboardResponse adminDashboard();

    /** Sparkline/chart time series for the admin dashboard, over the trailing N days. */
    AdminDashboardTrendsResponse adminDashboardTrends(int days);

    /** Score dashboard for the given student user. */
    ScoreDashboardResponse studentDashboard(UUID studentUserId);

    /**
     * Score dashboard for one of the parent's linked students. {@code requestedStudentId}
     * selects which child for a multi-child parent; null defaults to their first-linked child.
     */
    ScoreDashboardResponse parentDashboard(UUID parentUserId, UUID requestedStudentId);
}
