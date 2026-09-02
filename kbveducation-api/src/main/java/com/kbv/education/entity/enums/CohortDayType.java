package com.kbv.education.entity.enums;

import java.util.Set;

/**
 * How a specific calendar date is classified for a specific cohort. Unconfigured
 * dates default to {@link #LESSON_DAY} (see {@code CohortDayServiceImpl}), so
 * existing cohorts with no configured days behave exactly as before this feature.
 *
 * <p>{@code REST_DAY} and {@code SKIP_DAY} currently have the identical effect on
 * scoring (both are excluded from the Practice %/Reflection % "available days"
 * denominator, the same way a voided {@code StudyDay} is) — the distinction is
 * kept for attendance-grid display and future business rules, but no rule
 * anywhere in the existing app defines a scoring difference between them, so
 * none is invented here.</p>
 */
public enum CohortDayType {
    LESSON_DAY,
    REST_DAY,
    SKIP_DAY;

    /** Both excluded from the scoring "available days" denominator — see the class doc. */
    public static final Set<CohortDayType> NON_LESSON_TYPES = Set.of(REST_DAY, SKIP_DAY);
}
