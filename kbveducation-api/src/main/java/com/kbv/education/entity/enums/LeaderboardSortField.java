package com.kbv.education.entity.enums;

/**
 * Metric a leaderboard can be ranked by. A snapshot is cached per field so
 * every sort order is pre-computed.
 */
public enum LeaderboardSortField {
    COMPOSITE,
    PRACTICE,
    QUIZ,
    REFLECTION,
    HOMEWORK
}
