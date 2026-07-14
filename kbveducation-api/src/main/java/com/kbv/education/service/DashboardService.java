package com.kbv.education.service;

import com.kbv.education.dto.response.AdminDashboardResponse;
import com.kbv.education.dto.response.ScoreDashboardResponse;

import java.util.UUID;

public interface DashboardService {

    AdminDashboardResponse adminDashboard();

    /** Score dashboard for the given student user. */
    ScoreDashboardResponse studentDashboard(UUID studentUserId);

    /** Score dashboard for the student linked to the given parent user. */
    ScoreDashboardResponse parentDashboard(UUID parentUserId);
}
