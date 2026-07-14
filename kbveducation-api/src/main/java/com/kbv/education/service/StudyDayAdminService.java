package com.kbv.education.service;

import java.util.UUID;

public interface StudyDayAdminService {

    /** Marks a study day as void (excluded from Practice %/Reflection % calculations). */
    void voidDay(UUID studyDayId, String reason, UUID adminId);
}
