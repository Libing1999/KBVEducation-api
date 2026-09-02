package com.kbv.education.service;

import com.kbv.education.dto.cohortday.CohortDayResponse;
import com.kbv.education.entity.enums.CohortDayType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CohortDayService {

    /**
     * One entry per date in {@code [from, to]} for this cohort — configured dates carry their
     * set {@code dayType}; unconfigured dates default to {@code LESSON_DAY} ({@code configured=false}).
     */
    List<CohortDayResponse> list(UUID cohortId, LocalDate from, LocalDate to);

    /** Sets (or changes) a date's classification for this cohort. */
    CohortDayResponse upsert(UUID cohortId, LocalDate date, CohortDayType dayType, UUID adminId);

    /** Removes a date's override, reverting it to the default (LESSON_DAY). */
    void reset(UUID cohortId, LocalDate date, UUID adminId);
}
