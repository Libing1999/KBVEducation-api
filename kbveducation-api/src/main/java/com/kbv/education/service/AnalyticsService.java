package com.kbv.education.service;

import com.kbv.education.dto.analytics.AdminAnalyticsResponse;
import com.kbv.education.dto.analytics.StudentTrendResponse;
import com.kbv.education.dto.analytics.TrendPointResponse;
import com.kbv.education.entity.enums.LeaderboardSortField;

import java.util.List;
import java.util.UUID;

public interface AnalyticsService {

    /** Recomputes and caches all aggregate metrics. {@code cohortId} null = platform-wide. */
    void refresh(UUID cohortId);

    /** Reads the cached metrics, computing them on first access if the cache is empty. */
    AdminAnalyticsResponse get(UUID cohortId);

    /**
     * Cohort-wide daily average of one metric, bucketed by the day each contributing
     * recalculation happened (not a point-in-time reconstruction for every calendar day).
     */
    List<TrendPointResponse> trend(UUID cohortId, LeaderboardSortField metric, int days);

    /** Per-student composite-score trend, e.g. for the current top-N leaderboard entries. */
    List<StudentTrendResponse> studentTrend(List<UUID> studentIds, int days);
}
