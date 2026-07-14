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
}
