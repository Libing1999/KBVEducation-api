package com.kbv.education.service;

import java.time.LocalDate;
import java.util.UUID;

public interface StudyDayAdminService {

    /**
     * Marks a student's day as void (excluded from Practice %/Reflection % calculations).
     * Creates the underlying study day record if the student had no activity logged for
     * that date yet — voiding must work for a day the student did nothing on too (e.g.
     * illness), not only one with existing activity.
     */
    void voidDay(UUID studentId, LocalDate date, String reason, UUID adminId);

    /** Reverts a previously voided day back to counting normally. */
    void unvoidDay(UUID studentId, LocalDate date, UUID adminId);
}
