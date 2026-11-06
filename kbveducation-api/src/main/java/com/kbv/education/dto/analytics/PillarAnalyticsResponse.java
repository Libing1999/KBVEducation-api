package com.kbv.education.dto.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One pillar of the new Analytics screen (memo "AI plan" 17.10.2026, section 2). Admin only.
 *
 * <p>When "Who" is more than one student, every point on a time chart is one day: the average
 * for scores, ratings, lengths and averages, the total for counts, and {@code contributors}
 * lists the students behind it (click to go one level down). When "Who" is one student, every
 * point is one session or submission and {@code refId} points to it.</p>
 */
public record PillarAnalyticsResponse(
        String pillar,
        LocalDate from,
        LocalDate to,
        boolean singleStudent,
        List<StudentRef> students,
        List<Stat> stats,
        List<Chart> charts,
        List<Table> tables
) {

    public record StudentRef(UUID id, String name) {
    }

    /** A headline number for the chosen dates, e.g. "Sessions: 42". */
    public record Stat(String label, String value) {
    }

    /**
     * @param kind        "line" (over time) or "bar" (totals)
     * @param aggregation "avg" or "sum": how a day's point is made from many students
     * @param drill       what {@code refId} points to: "practice", "reflection", "homework", "quiz" or "none"
     * @param stacked     bar charts only: whether the series stack into one bar
     */
    public record Chart(String key, String title, String unit, String kind, String aggregation, String drill,
                        boolean stacked, List<Series> series) {
    }

    public record Series(String name, List<Point> points) {
    }

    /**
     * @param x     ISO date for time charts, a category name for bar charts
     * @param label the exact value as shown, e.g. "38/50" for a past paper score
     */
    public record Point(String x, double value, String label, UUID refId, List<Contributor> contributors) {
    }

    public record Contributor(UUID studentId, String name, double value) {
    }

    /** An exact-values table, e.g. answers to a text field. {@code refType} says where a row links. */
    public record Table(String key, String title, List<String> columns, List<Row> rows) {
    }

    public record Row(List<String> cells, String refType, UUID refId) {
    }
}
