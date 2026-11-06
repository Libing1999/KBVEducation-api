package com.kbv.education.service.analytics;

import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Contributor;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Point;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Series;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Turns raw observations into chart points (memo 17.10.2026, 2.5): one point per day with the
 * students behind it, or one point per item for a single student.
 */
final class SeriesBuilder {

    enum Agg { AVG, SUM }

    /** One measured value: e.g. one session's engagement rating, or "1" for one session logged. */
    record Obs(LocalDate date, UUID studentId, double value, UUID refId, String label) {
    }

    private SeriesBuilder() {
    }

    /**
     * @param perItem one point per observation (single student) instead of one per day
     */
    static Series series(String name, List<Obs> obs, Agg agg, boolean perItem, Map<UUID, String> names) {
        if (perItem) {
            List<Point> points = obs.stream()
                    .sorted(Comparator.comparing(Obs::date))
                    .map(o -> new Point(o.date().toString(), round(o.value()), o.label(), o.refId(), null))
                    .toList();
            return new Series(name, points);
        }
        Map<LocalDate, List<Obs>> byDay = new TreeMap<>();
        for (Obs o : obs) {
            byDay.computeIfAbsent(o.date(), d -> new ArrayList<>()).add(o);
        }
        List<Point> points = new ArrayList<>();
        byDay.forEach((day, list) -> {
            double total = list.stream().mapToDouble(Obs::value).sum();
            double value = agg == Agg.SUM ? total : total / list.size();
            // The students behind the point, each with their own value for that day.
            Map<UUID, List<Double>> perStudent = new LinkedHashMap<>();
            for (Obs o : list) {
                perStudent.computeIfAbsent(o.studentId(), s -> new ArrayList<>()).add(o.value());
            }
            List<Contributor> contributors = new ArrayList<>();
            perStudent.forEach((sid, values) -> {
                double sum = values.stream().mapToDouble(Double::doubleValue).sum();
                contributors.add(new Contributor(sid, names.getOrDefault(sid, "Unknown"),
                        round(agg == Agg.SUM ? sum : sum / values.size())));
            });
            contributors.sort(Comparator.comparing(Contributor::name));
            points.add(new Point(day.toString(), round(value), null, null, contributors));
        });
        return new Series(name, points);
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
