package com.kbv.education.service;

import com.kbv.education.dto.response.PageResponse;
import com.kbv.education.dto.score.StudentScoreResponse;
import com.kbv.education.entity.enums.ScoreTriggerReason;

import java.util.UUID;

public interface ScoreEngineService {

    /** Recalculates one student's composite score and stores it as the new current row. */
    StudentScoreResponse recalculate(UUID studentId, ScoreTriggerReason reason);

    /** Recalculates every actively-enrolled student in a cohort. */
    void recalculateForCohort(UUID cohortId, ScoreTriggerReason reason);

    /** Recalculates every student in every active cohort (score-config changes affect everyone). */
    void recalculateAll(ScoreTriggerReason reason);

    /** The student's current score, calculating it on first access if none exists yet. */
    StudentScoreResponse getCurrent(UUID studentId);

    PageResponse<StudentScoreResponse> getHistory(UUID studentId, int page, int size);

    /**
     * Real trailing-window pace projection: {@code atRecentPace}/{@code last3Days} recompute the
     * composite score using only the practice/reflection completion rate over the trailing 7 (resp.
     * 3) days, holding homework/quiz at their current values (those aren't day-windowed). Never a
     * fabricated/static number — every field is derived from actual student_scores/study_days data.
     */
    PaceProjection getPaceProjection(UUID studentId);

    record PaceProjection(double now, double atRecentPace, double last3Days,
                           Double nextTierThreshold, String nextTierName) {
    }
}
