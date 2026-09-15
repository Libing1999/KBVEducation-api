package com.kbv.education.dto.cohortday;

import com.kbv.education.entity.enums.CohortDayType;

import java.time.LocalDate;

/** One date's classification for a cohort. {@code configured=false} means no admin override
 *  exists for this date — {@code dayType} is the default (LESSON_DAY). */
public record CohortDayResponse(LocalDate date, CohortDayType dayType, boolean configured) {
}
