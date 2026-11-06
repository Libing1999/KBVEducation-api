package com.kbv.education.service.analytics;

import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Point;
import com.kbv.education.dto.analytics.PillarAnalyticsResponse.Series;
import com.kbv.education.service.analytics.SeriesBuilder.Agg;
import com.kbv.education.service.analytics.SeriesBuilder.Obs;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chart points for the Analytics screen (memo 17.10.2026, 2.5). */
class SeriesBuilderTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final Map<UUID, String> NAMES = Map.of(ANA, "Ana", BEN, "Ben");
    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 2);

    private static final List<Obs> OBS = List.of(
            new Obs(D2, ANA, 6, UUID.randomUUID(), null),
            new Obs(D1, ANA, 8, UUID.randomUUID(), null),
            new Obs(D1, ANA, 4, UUID.randomUUID(), null),
            new Obs(D1, BEN, 3, UUID.randomUUID(), null));

    @Test
    void groupModeAveragesEachDayAndListsTheStudentsBehindIt() {
        Series s = SeriesBuilder.series("Engagement", OBS, Agg.AVG, false, NAMES);

        assertThat(s.points()).extracting(Point::x).containsExactly("2026-10-01", "2026-10-02");
        Point day1 = s.points().get(0);
        assertThat(day1.value()).isEqualTo(5.0);
        assertThat(day1.contributors()).extracting(c -> c.name() + "=" + c.value())
                .containsExactly("Ana=6.0", "Ben=3.0");
    }

    @Test
    void groupModeTotalsCounts() {
        Series s = SeriesBuilder.series("Sessions", OBS, Agg.SUM, false, NAMES);

        assertThat(s.points()).extracting(Point::value).containsExactly(15.0, 6.0);
    }

    @Test
    void singleStudentGetsOnePointPerItemInDateOrder() {
        Series s = SeriesBuilder.series("Engagement", OBS, Agg.AVG, true, NAMES);

        assertThat(s.points()).hasSize(4);
        assertThat(s.points().get(0).x()).isEqualTo("2026-10-01");
        assertThat(s.points().get(3).value()).isEqualTo(6.0);
        assertThat(s.points()).allSatisfy(p -> assertThat(p.refId()).isNotNull());
    }

    @Test
    void wordsCountsWhitespaceSeparatedWords() {
        assertThat(PillarAnalyticsService.words("  I revised  algebra\ntoday ")).isEqualTo(4);
        assertThat(PillarAnalyticsService.words(null)).isZero();
    }
}
