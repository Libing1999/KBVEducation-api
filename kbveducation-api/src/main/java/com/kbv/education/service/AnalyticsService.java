package com.kbv.education.service;

import com.kbv.education.dto.analytics.AdminAnalyticsResponse;

import java.util.UUID;

public interface AnalyticsService {

    /** Recomputes and caches all aggregate metrics. {@code cohortId} null = platform-wide. */
    void refresh(UUID cohortId);

    /** Reads the cached metrics, computing them on first access if the cache is empty. */
    AdminAnalyticsResponse get(UUID cohortId);
}
